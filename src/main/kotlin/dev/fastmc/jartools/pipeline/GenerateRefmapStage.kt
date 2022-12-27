package dev.fastmc.jartools.pipeline

import dev.fastmc.asmkt.visit
import dev.fastmc.jartools.mapping.*
import dev.fastmc.jartools.util.annotations
import dev.fastmc.jartools.util.findAnnotation
import dev.fastmc.jartools.util.findAnyAnnotation
import dev.fastmc.jartools.util.findMixinAnnotation
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

class GenerateRefmapStage(private val mappingPipeline: MappingPipeline) : Stage {
    override suspend fun run(files: Map<String, JarEntry>): Map<String, JarEntry> {
        val result = Object2ObjectOpenHashMap(files)
        val classEntries = files.values.filterIsInstance<ClassEntry>()
        val mixinReferMap = Object2ObjectOpenHashMap<String, Object2ObjectOpenHashMap<String, String>>()
        val mapping = mappingPipeline.get(classEntries)

        coroutineScope {
            classEntries.forEach { classEntry ->
                val classNode = classEntry.classNode
                val mixinAnnotation = classNode.findMixinAnnotation() ?: return@forEach

                val classRefmap = Object2ObjectOpenHashMap<String, String>()
                require(mixinReferMap.put(classNode.name, classRefmap) == null) {
                    "Duplicate mixin class ${classNode.name}"
                }

                var remapping = true
                val valueList = ObjectArrayList<Type>()
                val targetList = ObjectArrayList<String>()
                mixinAnnotation.visit {
                    visitValue<Boolean>("remap") {
                        remapping = it
                    }
                    visitArray("value") {
                        valueList.addAll(it)
                    }
                    visitArray("targets") {
                        targetList.addAll(it)
                    }
                }
                if (!remapping) return@forEach

                val targetClassList = ObjectArrayList<String>(valueList.size + targetList.size)
                valueList.mapTo(targetClassList, Type::getClassName)
                targetClassList.addAll(targetList)

                launch {
                    targetClassList.forEach {
                        val classMappingEntry = mapping[it]
                            ?: throw IllegalStateException("Cannot find mapping for target class $it at ${classNode.name}")

                        mapMethods(classMappingEntry, it, classNode, mapping, classRefmap)
                        mapAccessors(mapping, classNode, classMappingEntry, classRefmap)
                    }
                }

                remapMixinTarget(mapping, classEntry, valueList, targetList, result)
            }
        }

        return result
    }

    private fun remapMixinTarget(
        mapping: ClassMapping,
        classEntry: ClassEntry,
        valueList: ObjectArrayList<Type>,
        targetList: ObjectArrayList<String>,
        result: Object2ObjectOpenHashMap<String, JarEntry>
    ) {
        val classNode = classEntry.classNode

        var remapTargetClass = false
        val newValue = valueList.map {
            val nameTo = mapping.getNameTo(it.className)
                ?: throw IllegalStateException("Cannot find mapping for target class $it at ${classNode.name}")
            if (nameTo == it.className) {
                it
            } else {
                remapTargetClass = true
                Type.getObjectType(nameTo)
            }
        }
        val newTargets = targetList.map {
            val nameTo = mapping.getNameTo(it)
                ?: throw IllegalStateException("Cannot find mapping for target class $it at ${classNode.name}")
            if (nameTo == it) {
                it
            } else {
                remapTargetClass = true
                nameTo
            }
        }
        if (remapTargetClass) {
            val newClassNode = ClassNode()
            classNode.accept(newClassNode)
            val newMixinAnnotation = newClassNode.findMixinAnnotation()!!
            for (i in 0 until newMixinAnnotation.values.size) {
                when (newMixinAnnotation.values[i]) {
                    "value" -> newMixinAnnotation.values[i + 1] = newValue
                    "targets" -> newMixinAnnotation.values[i + 1] = newTargets
                }
            }
            result.put(classEntry.update(classNode))
        }
    }

    private fun mapMethods(
        classMappingEntry: MappingEntry.Class,
        targetClass: String,
        classNode: ClassNode,
        mapping: ClassMapping,
        classRefmap: Object2ObjectOpenHashMap<String, String>
    ) {
        val classNameTo = classMappingEntry.nameTo
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
                    if (remappingMethod) {
                        methods.forEach { methodRef ->
                            val match = methodRefRegex.matchEntire(methodRef)
                            if (match != null) {
                                val nameFrom = match.groupValues[1]
                                val descFrom = match.groupValues[2]
                                val nameTo = classMappingEntry.methodMapping.getNameTo(nameFrom, descFrom)
                                val descTo = mapping.remapDesc(descFrom)
                                classRefmap[methodRef] = "$classNameTo;$nameTo$descTo"
                            } else if (methodRef.matches(wildcardMethodRefRegex)) {
                                error("Wildcard method reference is not supported")
                            } else {
                                val nameFrom = methodRef
                                val methodEntry = classMappingEntry.methodMapping.backingMap?.find {
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
            }
    }

    private fun mapAccessors(
        mapping: ClassMapping,
        classNode: ClassNode,
        classMappingEntry: MappingEntry.Class,
        classRefmap: Object2ObjectOpenHashMap<String, String>
    ) {
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
                    nameFrom = nameFrom!!.replaceFirstChar(Char::lowercaseChar)
                }
                val nameTo = classMappingEntry.fieldMapping.getNameTo(nameFrom!!) ?: return@field
                classRefmap[fieldNode.name] = "$nameTo:${classMappingEntry.nameTo}"
            }
    }

    private companion object {
        val methodRefRegex = "([^;/\\s\\n]+)(\\(.*\\).+)".toRegex()
        val wildcardMethodRefRegex = "([^;/\\s\\n]+)\\*".toRegex()
        val fullMethodRefRegex = "L(.+;)(.+)(\\(.*\\).+)".toRegex()
    }
}