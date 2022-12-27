package dev.fastmc.jartools.pipeline

import dev.fastmc.asmkt.visit
import dev.fastmc.jartools.mapping.ClassMapping
import dev.fastmc.jartools.mapping.get
import dev.fastmc.jartools.mapping.getNameTo
import dev.fastmc.jartools.mapping.remapDesc
import dev.fastmc.jartools.util.annotations
import dev.fastmc.jartools.util.findAnnotation
import dev.fastmc.jartools.util.findAnyAnnotation
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode

class GenerateRefmapStage(private val deferred: Deferred<ClassMapping>) : Stage {
    override suspend fun run(files: Map<String, JarEntry>): Map<String, JarEntry> {
        return coroutineScope {
            val result = Object2ObjectOpenHashMap(files)
            val mixinReferMap = Object2ObjectOpenHashMap<String, Object2ObjectOpenHashMap<String, String>>()

            result.values
                .filterIsInstance<ClassEntry>()
                .forEach { classEntry ->
                    val classNode = classEntry.classNode
                    val mixinClassAnnotation = classNode.annotations
                        .findAnnotation("Lorg/spongepowered/asm/mixin/Mixin;") ?: return@forEach
                    var targetClass0: String? = null
                    var remapping = true
                    mixinClassAnnotation.visit {
                        visitValue<Boolean>("remap") {
                            remapping = it
                        }
                        visitArray<Type>("value") {
                            check(it.size == 1) { "Only support one target class" }
                            targetClass0 = it[0].className
                        }
                    }
                    val targetClass = targetClass0!!
                    val mapping = deferred.await()
                    val classMappingEntry = if (remapping) mapping[targetClass] else null
                    val classNameTo = classMappingEntry?.nameTo ?: targetClass
                    val classRefmap = mixinReferMap.getOrPut(classNode.name, ::Object2ObjectOpenHashMap)
                    val methodRefRegex = "([^;/\\s\\n]+)(\\(.*\\).+)".toRegex()
                    val wildcardMethodRefRegex = "([^;/\\s\\n]+)\\*".toRegex()
                    val fullMethodRefRegex = "L(.+;)(.+)(\\(.*\\).+)".toRegex()

                    val methods = ObjectArrayList<String>()
                    var remappingMethod = true
                    val atMethods = ObjectArrayList<String>()
                    var remappingAtMethod = true

                    classNode.methods.asSequence()
                        .mapNotNull {
                            it.annotations.findAnyAnnotation(
                                "Lorg/spongepowered/asm/mixin/injection/Inject;",
                                "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
                                "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
                                "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
                                "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
                                "Lorg/spongepowered/asm/mixin/injection/Redirect;",
                            )
                        }.forEach { annotationNode ->
                            annotationNode.visit {
                                visitValue<Boolean>("remap") {
                                    remappingMethod = it
                                }
                                visitArray<String>("method") {
                                    methods.addAll(it)
                                }
                                visitArray<AnnotationNode>("at") { list ->
                                    list.filter {
                                        it.desc == "Lorg/spongepowered/asm/mixin/injection/At;"
                                    }.forEach { atAnnotationNode ->
                                        atAnnotationNode.visit {
                                            visitValue<String>("target") {
                                                atMethods.add(it)
                                            }
                                            visitValue<Boolean>("remap") {
                                                remappingAtMethod = it
                                            }
                                        }
                                        if (remappingAtMethod) {
                                            atMethods.forEach {
                                                val match = fullMethodRefRegex.matchEntire(it)
                                                if (match != null) {
                                                    val ownerFrom = match.groupValues[1]
                                                    val nameFrom = match.groupValues[2]
                                                    val descFrom = match.groupValues[3]

                                                    val targetEntry = mapping[ownerFrom]
                                                    val ownerTo = targetEntry?.nameTo ?: ownerFrom
                                                    val nameTo =
                                                        targetEntry?.methodMapping?.getNameTo(nameFrom, descFrom)
                                                    val descTo = mapping.remapDesc(descFrom)
                                                    classRefmap[it] = "$ownerTo;$nameTo$descTo"
                                                }
                                            }
                                        }
                                        atMethods.clear()
                                        remappingAtMethod = true
                                    }
                                }
                            }
                            if (remappingMethod) {
                                methods.forEach { methodRef ->
                                    val match = methodRefRegex.matchEntire(methodRef)
                                    if (match != null) {
                                        val nameFrom = match.groupValues[1]
                                        val descFrom = match.groupValues[2]
                                        val nameTo = classMappingEntry?.methodMapping?.getNameTo(nameFrom, descFrom)
                                        val descTo = mapping.remapDesc(descFrom)
                                        classRefmap[methodRef] = "$classNameTo;$nameTo$descTo"
                                    } else if (methodRef.matches(wildcardMethodRefRegex)) {
                                        error("Wildcard method reference is not supported")
                                    } else {
                                        val nameFrom = methodRef
                                        val methodEntry = classMappingEntry?.methodMapping?.backingMap?.find {
                                            it.nameFrom.startsWith(nameFrom)
                                        } ?: throw IllegalStateException("Cannot find method $nameFrom in $targetClass")
                                        val descFrom = methodEntry.desc

                                        val nameTo = methodEntry.nameTo
                                        val descTo = mapping.remapDesc(descFrom)
                                        classRefmap[methodRef] = "$classNameTo;$nameTo$descTo"
                                    }
                                }
                            }
                            methods.clear()
                            remappingMethod = true
                        }

                    if (classMappingEntry != null) {
                        classNode.methods.asSequence()
                            .mapNotNull { methodNode ->
                                methodNode.annotations.findAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;")
                                    ?.let {
                                        methodNode to it
                                    }
                            }.forEach method@{ (methodNode, annotation) ->
                                var nameFrom: String? = null
                                for (i in 0 until annotation.values.size step 2) {
                                    when (annotation.values[i] as String) {
                                        "value" -> {
                                            nameFrom = annotation.values[i + 1] as String
                                        }
                                    }
                                }
                                if (nameFrom == null) {
                                    nameFrom = when {
                                        methodNode.name.startsWith("invoke") -> {
                                            methodNode.name.substring(6)
                                        }
                                        methodNode.name.startsWith("call") -> {
                                            methodNode.name.substring(4)
                                        }
                                        else -> {
                                            throw IllegalStateException("Cannot find name for invoker ${methodNode.name}")
                                        }
                                    }
                                }
                                val descFrom = methodNode.desc
                                val nameTo =
                                    classMappingEntry.methodMapping.getNameTo(nameFrom, descFrom) ?: return@method
                                val descTo = mapping.remapDesc(descFrom)
                                classRefmap[methodNode.name] = "$nameTo$descTo"
                            }

                        classNode.fields.asSequence()
                            .mapNotNull { methodNode ->
                                methodNode.annotations.findAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;")
                                    ?.let {
                                        methodNode to it
                                    }
                            }.forEach field@{ (fieldNode, annotation) ->
                                var nameFrom: String? = null
                                for (i in 0 until annotation.values.size step 2) {
                                    when (annotation.values[i] as String) {
                                        "value" -> {
                                            nameFrom = annotation.values[i + 1] as String
                                        }
                                    }
                                }
                                if (nameFrom == null) {
                                    nameFrom = when {
                                        fieldNode.name.startsWith("get") -> {
                                            fieldNode.name.substring(3)
                                        }
                                        fieldNode.name.startsWith("set") -> {
                                            fieldNode.name.substring(3)
                                        }
                                        else -> {
                                            throw IllegalStateException("Cannot find name for accessor ${fieldNode.name}")
                                        }
                                    }
                                }
                                val nameTo = classMappingEntry.fieldMapping.getNameTo(nameFrom!!) ?: return@field
                                classRefmap[fieldNode.name] = "$nameTo:${classMappingEntry.nameTo}"
                            }
                    }

                    result[classEntry.fileName] = classEntry.update(classNode)
                }

            result
        }
    }
}