package dev.luna5ama.jartools.remap

import dev.luna5ama.jartools.util.SubclassInfo
import dev.luna5ama.jartools.util.annotations
import dev.luna5ama.jartools.util.containsAnnotation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.io.File

interface MappingProvider {
    suspend fun get(
        prev: Deferred<Mapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): Mapping

    suspend fun get(
        classNodes: Deferred<Collection<ClassNode>>
    ): Mapping {
        return get(null, classNodes)
    }
}

class SubClassMappingProvider(private val inputClassNodes: Deferred<Collection<ClassNode>>) : MappingProvider {
    override suspend fun get(
        prev: Deferred<Mapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): Mapping {
        require(prev != null) { "SubClassMappingProvider requires a previous mapping" }
        return coroutineScope {
            val subclassInfoDeferred = async { SubclassInfo(inputClassNodes.await()) }

            val result: MutableMapping = hashMapOf()
            prev.await().forEach {
                result[it.key] = it.value.toMutable()
            }

            subclassInfoDeferred.await().forEach { info ->
                val currentClassMapping = result[info.classNode.name]
                if (currentClassMapping != null) {
                    info.subclasses.forEach { subclass ->
                        var set: MutableClassMapping? = null
                        currentClassMapping.methods.forEach {
                            if (set == null) {
                                set = result.getOrPutEmpty(subclass.classNode.name)
                            }
                            set!!.addMethod(MappingEntry.Method(it.nameFrom, it.desc, it.nameTo))
                        }
                        currentClassMapping.fields.forEach {
                            if (subclass.classNode.fields.none { fieldNode -> fieldNode.name == it.nameFrom }) {
                                if (set == null) {
                                    set = result.getOrPutEmpty(subclass.classNode.name)
                                }
                                set!!.addField(MappingEntry.Field(it.nameFrom, it.nameTo))
                            }
                        }
                    }
                }
            }

            result
        }
    }
}

class MixinMappingProvider(private val mixinClassesDeferred: Deferred<Map<String, ClassNode>>) : MappingProvider {
    override suspend fun get(
        prev: Deferred<Mapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): Mapping {
        require(prev != null) { "MixinMappingProvider requires a previous mapping" }
        return coroutineScope {
            val result: MutableMapping = hashMapOf()
            val prevMapping = prev.await()
            prevMapping.forEach {
                result[it.key] = it.value.toMutable()
            }

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
                                                prevMapping[value.internalName]?.let { mixinMapping ->
                                                    var set: MutableClassMapping? = null
                                                    mixinMapping.methods.forEach {
                                                        if (set == null) {
                                                            set = result.getOrPutEmpty(classNode.name)
                                                        }
                                                        set!!.addMethod(it)
                                                    }

                                                    mixinMapping.fields.forEach loop@{ mapping ->
                                                        if (classNode.fields.none { fieldNode ->
                                                                fieldNode.name == mapping.nameFrom
                                                                    && fieldNode.annotations.containsAnnotation("Lorg/spongepowered/asm/mixin/Shadow;")
                                                            }) return@loop

                                                        if (set == null) {
                                                            set = result.getOrPutEmpty(classNode.name)
                                                        }
                                                        set!!.addField(mapping)
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

            result
        }
    }
}

class SequenceMappingProvider(private vararg val providers: MappingProvider) : MappingProvider {
    init {
        require(providers.isNotEmpty()) { "No providers provided" }
    }

    private var cached: Mapping? = null

    override suspend fun get(
        prev: Deferred<Mapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): Mapping {
        val cached = cached
        if (cached != null) return cached

        var prevDeferred: Deferred<Mapping>? = prev
        return coroutineScope {
            val deferredList = providers.map {
                val argDeferred = prevDeferred
                val current = async { it.get(argDeferred, classNodes) }
                prevDeferred = current
                current
            }

            val result: MutableMapping = hashMapOf()
            deferredList.forEach { deferred ->
                mergeMapping(result, deferred.await())
            }

            this@SequenceMappingProvider.cached = result
            result
        }
    }
}

class TsrgMappingProvider(private val file: File) : MappingProvider {
    init {
        require(file.extension == "tsrg") { "Invalid file $file" }
    }

    private val cached by lazy<Mapping> {
        var skippedHeader = false
        val result: MutableMapping = hashMapOf()
        var lastClassMapping: MutableClassMapping? = null
        file.forEachLine {
            if (!skippedHeader) {
                skippedHeader = true
                return@forEachLine
            }

            if (!it.startsWith("\t")) {
                val className = it.substringBefore(' ')
                lastClassMapping = MutableClassMapping(className)
                result[className] = lastClassMapping!!
                return@forEachLine
            }

            val split = it.substring(1).split(' ')
            if (split.size == 3) {
                lastClassMapping!!.addMethod(MappingEntry.Method(split[0], split[1], split[2]))
            } else {
                lastClassMapping!!.addField(MappingEntry.Field(split[0], split[1]))
            }
        }

        result
    }

    override suspend fun get(
        prev: Deferred<Mapping>?,
        classNodes: Deferred<Collection<ClassNode>>
    ): Mapping {
        return cached
    }
}