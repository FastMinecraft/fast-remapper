package dev.fastmc.jartools.mapping

import dev.fastmc.jartools.util.SubclassInfo
import dev.fastmc.jartools.util.annotations
import dev.fastmc.jartools.util.containsAnnotation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

interface MappingPipeline {
    suspend fun get(
        prev: Deferred<ClassMapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): ClassMapping

    suspend fun get(
        classNodes: Deferred<Collection<ClassNode>>
    ): ClassMapping {
        return get(null, classNodes)
    }
}

class SubClassRemapPipeline(private val inputClassNodes: Deferred<Collection<ClassNode>>) : MappingPipeline {
    override suspend fun get(
        prev: Deferred<ClassMapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): ClassMapping {
        require(prev != null) { "SubClassRemapPipeline requires a previous mapping" }
        return coroutineScope {
            val subclassInfoDeferred = async { SubclassInfo(inputClassNodes.await()) }

            val result: MutableClassMapping = MutableClassMapping()
            result.addAll(prev.await())

            subclassInfoDeferred.await().forEach { info ->
                val currentClassMapping = result.get(info.classNode.name)
                if (currentClassMapping != null) {
                    info.subclasses.forEach { subclass ->
                        var set: MappingEntry.MutableClass? = null
                        currentClassMapping.methodMapping.backingMap.forEachFast {
                            if (set == null) {
                                set = result.getOrCreate(subclass.classNode.name)
                            }
                            set!!.methodMapping.add(MappingEntry.Method(it.nameFrom, it.desc, it.nameTo))
                        }
                        currentClassMapping.fieldMapping.backingMap.forEachFast {
                            if (subclass.classNode.fields.none { fieldNode -> fieldNode.name == it.nameFrom }) {
                                if (set == null) {
                                    set = result.getOrCreate(subclass.classNode.name)
                                }
                                set!!.fieldMapping.add(MappingEntry.Field(it.nameFrom, it.nameTo))
                            }
                        }
                    }
                }
            }

            result.asImmutable()
        }
    }
}

class MixinRemapPipeline(private val mixinClassesDeferred: Deferred<Map<String, ClassNode>>) : MappingPipeline {
    override suspend fun get(
        prev: Deferred<ClassMapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): ClassMapping {
        require(prev != null) { "MixinRemapPipeline requires a previous mapping" }
        return coroutineScope {
            val result: MutableClassMapping = MutableClassMapping()
            val prevMapping = prev.await()
            result.addAll(prevMapping)

            mixinClassesDeferred.await().values.forEach { classNode ->
                classNode.accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                        return if (descriptor == "Lorg/spongepowered/asm/mixin/Mixin;") {
                            object : AnnotationVisitor(Opcodes.ASM9) {
                                override fun visitArray(name: String): AnnotationVisitor? {
                                    return if (name == "value" || name == "targets") {
                                        object : AnnotationVisitor(Opcodes.ASM9) {
                                            override fun visit(n: String?, value: Any) {
                                                if (value !is Type) return
                                                prevMapping.get(value.internalName)?.let { mixinMapping ->
                                                    var set: MappingEntry.MutableClass? = null
                                                    mixinMapping.methodMapping.backingMap.forEachFast {
                                                        if (set == null) {
                                                            set = result.getOrCreate(classNode.name)
                                                        }
                                                        set!!.methodMapping.add(it)
                                                    }

                                                    mixinMapping.fieldMapping.backingMap.forEachFast loop@{ mapping ->
                                                        if (classNode.fields.none { fieldNode ->
                                                                fieldNode.name == mapping.nameFrom
                                                                        && fieldNode.annotations.containsAnnotation("Lorg/spongepowered/asm/mixin/Shadow;")
                                                            }) return@loop

                                                        if (set == null) {
                                                            set = result.getOrCreate(classNode.name)
                                                        }
                                                        set!!.fieldMapping.add(mapping)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        super.visitArray(name)
                                    }
                                }
                            }
                        } else {
                            super.visitAnnotation(descriptor, visible)
                        }
                    }
                })
            }

            result.asImmutable()
        }
    }
}

class SequenceRemapPipeline(private vararg val providers: MappingPipeline) : MappingPipeline {
    init {
        require(providers.isNotEmpty()) { "No providers provided" }
    }

    private var cached: ClassMapping? = null

    override suspend fun get(
        prev: Deferred<ClassMapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): ClassMapping {
        val cached = cached
        if (cached != null) return cached

        var prevDeferred: Deferred<ClassMapping>? = prev
        return coroutineScope {
            val deferredList = providers.map {
                val argDeferred = prevDeferred
                val current = async { it.get(argDeferred, classNodes) }
                prevDeferred = current
                current
            }

            val result: MutableClassMapping = MutableClassMapping()
            deferredList.forEach { deferred ->
                result.addAll(deferred.await())
            }

            this@SequenceRemapPipeline.cached = result.asImmutable()
            result.asImmutable()
        }
    }
}

class MappingProviderPipeline(private val deferred: Deferred<ClassMapping>): MappingPipeline {
    override suspend fun get(
        prev: Deferred<ClassMapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): ClassMapping {
        return deferred.await()
    }
}