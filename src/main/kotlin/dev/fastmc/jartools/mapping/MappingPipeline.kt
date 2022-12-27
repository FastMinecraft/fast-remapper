package dev.fastmc.jartools.mapping

import dev.fastmc.asmkt.visit
import dev.fastmc.jartools.pipeline.ClassEntry
import dev.fastmc.jartools.util.SubclassInfo
import dev.fastmc.jartools.util.annotations
import dev.fastmc.jartools.util.containsAnnotation
import dev.fastmc.jartools.util.findMixinAnnotation
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

interface MappingPipeline {
    suspend fun get(prev: Deferred<ClassMapping>?, classEntries: Collection<ClassEntry>): ClassMapping

    suspend fun get(classEntries: Collection<ClassEntry>): ClassMapping {
        return get(null, classEntries)
    }
}

class SubclassMappingPipeline(private val externalClasses: Deferred<Collection<ClassEntry>>) : MappingPipeline {
    override suspend fun get(prev: Deferred<ClassMapping>?, classEntries: Collection<ClassEntry>): ClassMapping {
        require(prev != null) { "SubclassMappingPipeline requires a previous mapping" }
        return coroutineScope {
            val subclassInfoDeferred = async {
                val inputClasses = Object2ObjectOpenHashMap<String, ClassNode>()
                classEntries.forEach {
                    inputClasses[it.className] = it.classNode
                }
                externalClasses.await().forEach {
                    inputClasses[it.className] = it.classNode
                }
                SubclassInfo(inputClasses.values)
            }

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

class MixinMappingPipeline() : MappingPipeline {
    override suspend fun get(prev: Deferred<ClassMapping>?, classEntries: Collection<ClassEntry>): ClassMapping {
        require(prev != null) { "MixinMappingPipeline requires a previous mapping" }
        return coroutineScope {
            val result: MutableClassMapping = MutableClassMapping()
            val prevMapping = prev.await()
            result.addAll(prevMapping)

            classEntries.forEach { classEntry ->
                val classNode = classEntry.classNode
                val mixinAnnotation = classNode.findMixinAnnotation() ?: return@forEach
                mixinAnnotation.visit {
                    visitArray<Type>("value") { v ->
                        v.forEach { type ->
                            prevMapping.get(type.internalName)?.let {
                                var set: MappingEntry.MutableClass? = null
                                it.methodMapping.backingMap.forEachFast {
                                    if (set == null) {
                                        set = result.getOrCreate(classNode.name)
                                    }
                                    set!!.methodMapping.add(it)
                                }

                                it.fieldMapping.backingMap.forEachFast loop@{ mapping ->
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
                    visitArray<String>("targets") { v ->
                        v.forEach { target ->
                            prevMapping.get(target)?.let {
                                var set: MappingEntry.MutableClass? = null
                                it.methodMapping.backingMap.forEachFast {
                                    if (set == null) {
                                        set = result.getOrCreate(classNode.name)
                                    }
                                    set!!.methodMapping.add(it)
                                }

                                it.fieldMapping.backingMap.forEachFast loop@{ mapping ->
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
                }
            }

            result.asImmutable()
        }
    }
}

class CachedMappingPipeline(private val pipeline: MappingPipeline) : MappingPipeline {
    private var cache: ClassMapping? = null
    private val mutex = Mutex()

    override suspend fun get(prev: Deferred<ClassMapping>?, classEntries: Collection<ClassEntry>): ClassMapping {
        return mutex.withLock {
            var mapping = cache
            if (mapping == null) {
                mapping = pipeline.get(prev, classEntries)
                cache = mapping
            }
            mapping
        }
    }
}

class SequenceMappingPipeline(private vararg val providers: MappingPipeline) : MappingPipeline {
    init {
        require(providers.isNotEmpty()) { "No providers provided" }
    }

    private var cached: ClassMapping? = null

    override suspend fun get(prev: Deferred<ClassMapping>?, classEntries: Collection<ClassEntry>): ClassMapping {
        val cached = cached
        if (cached != null) return cached

        var prevDeferred: Deferred<ClassMapping>? = prev
        return coroutineScope {
            val deferredList = providers.map {
                val argDeferred = prevDeferred
                val current = async { it.get(argDeferred, classEntries) }
                prevDeferred = current
                current
            }

            val result: MutableClassMapping = MutableClassMapping()
            deferredList.forEach { deferred ->
                result.addAll(deferred.await())
            }

            this@SequenceMappingPipeline.cached = result.asImmutable()
            result.asImmutable()
        }
    }
}

class MappingProviderPipeline(private val deferred: Deferred<ClassMapping>) : MappingPipeline {
    override suspend fun get(prev: Deferred<ClassMapping>?, classEntries: Collection<ClassEntry>): ClassMapping {
        return deferred.await()
    }
}