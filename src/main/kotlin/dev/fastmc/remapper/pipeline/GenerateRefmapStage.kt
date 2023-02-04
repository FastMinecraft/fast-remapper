package dev.fastmc.remapper.pipeline

import com.google.gson.*
import dev.fastmc.asmkt.visit
import dev.fastmc.remapper.mapping.*
import dev.fastmc.remapper.util.annotations
import dev.fastmc.remapper.util.findAnnotation
import dev.fastmc.remapper.util.findAnyAnnotation
import dev.fastmc.remapper.util.findMixinAnnotation
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter

class GenerateRefmapStage(
    private val mappingPipeline: MappingPipeline,
    private val refmapBaseName: String,
    private val mappingName: String,
    private val mixinConfigs: List<String>
) : Stage {
    override suspend fun run(files: Map<String, JarEntry>): Map<String, JarEntry> {
        return coroutineScope {
            val result = Object2ObjectOpenHashMap(files)
            val classEntries = files.values.filterIsInstance<ClassEntry>()
            val mixinReferMap = Object2ObjectOpenHashMap<String, Object2ObjectOpenHashMap<String, String>>()
            val mapping = mappingPipeline.get(classEntries)
            val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

            launch {
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
                        valueList.mapTo(targetClassList, Type::getInternalName)
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

                val refmapObj = mixinReferMap.entries
                    .filter { it.value.isNotEmpty() }
                    .map { entry -> entry.key to entry.value.toList().sortedBy { it.first } }
                    .sortedBy { it.first }
                    .run {
                        JsonObject().apply {
                            forEach { (k, v) ->
                                val o = JsonObject()
                                v.forEach {
                                    o.addProperty(it.first, it.second)
                                }
                                add(k, o)
                            }
                        }
                    }

                val obj = JsonObject()
                obj.add("mappings", refmapObj)
                obj.add("data", JsonObject().apply { add(mappingName, refmapObj) })
                val entry = JarFileEntry("$refmapBaseName.refmap.json", gson.toBytes(obj))

                synchronized(result) {
                    result.put(entry)
                }
            }

            mixinConfigs.forEach {
                val entry = result[it] as? JarFileEntry ?: return@forEach
                val obj = JsonParser.parseString(entry.bytes.decodeToString()).asJsonObject
                obj.addProperty("refmap", "$refmapBaseName.refmap.json")
                val newEntry = entry.update(gson.toBytes(obj))
                synchronized(result) {
                    result.put(newEntry)
                }
            }

            result
        }
    }

    private fun Gson.toBytes(
        jsonElement: JsonElement
    ): ByteArray {
        val output = ByteArrayOutputStream()
        OutputStreamWriter(output).use {
            val jsonWriter = this.newJsonWriter(it)
            jsonWriter.setIndent("    ")
            this.toJson(jsonElement, jsonWriter)
        }
        return output.toByteArray()
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
            val nameTo = mapping.getNameTo(it.internalName)
                ?: throw IllegalStateException("Cannot find mapping for target class $it at ${classNode.name}")
            if (nameTo == it.internalName) {
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
            newMixinAnnotation.values?.let {
                for (i in 0 until it.size) {
                    when (it[i]) {
                        "value" -> it[i + 1] = newValue
                        "targets" -> it[i + 1] = newTargets
                    }
                }
            }
            synchronized(result) {
                result.put(classEntry.update(classNode))
            }
        }
    }

    private fun mapMethods(
        classMappingEntry: MappingEntry.Class,
        targetClass: String,
        classNode: ClassNode,
        mapping: ClassMapping,
        classRefmap: Object2ObjectOpenHashMap<String, String>
    ) {
        val methods = ObjectArrayList<String>()
        var remappingMethod = true
        val atTargets = ObjectArrayList<String>()
        var remappingAtTargets = true

        classNode.methods.forEach { method ->
            val annotationNode = method.annotations.findAnyAnnotation(
                "Lorg/spongepowered/asm/mixin/injection/Inject;",
                "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
                "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
                "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
                "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
                "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            ) ?: return@forEach

            annotationNode.visit {
                visitValue<Boolean>("remap") {
                    remappingMethod = it
                }
                visitArray<String>("method") {
                    methods.addAll(it)
                }
                visitAnnotation("at", "Lorg/spongepowered/asm/mixin/injection/At;") {
                    visitValue<String>("target") {
                        atTargets.add(it)
                    }
                    visitValue<Boolean>("remap") {
                        remappingAtTargets = it
                    }
                }
            }

            if (remappingMethod) {
                methods.forEach method@{ methodRef ->
                    if (isInitMethod(methodRef)) {
                        mapMethodRef(
                            mapping,
                            targetClass,
                            classMappingEntry,
                            classRefmap,
                            methodRef,
                            methodRef,
                            ""
                        )
                    } else {
                        val match = methodRefRegex.matchEntire(methodRef)
                        if (match != null) {
                            val nameFrom = match.groupValues[1]
                            val descFrom = match.groupValues[2]
                            mapMethodRef(
                                mapping,
                                targetClass,
                                classMappingEntry,
                                classRefmap,
                                methodRef,
                                nameFrom,
                                descFrom
                            )
                        } else if (wildcardMethodRefRegex.matches(methodRef) || annotationNode.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;") {
                            if (annotationNode.desc != "Lorg/spongepowered/asm/mixin/injection/Inject;") {
                                throw IllegalStateException("Wildcard method reference is only allowed for @Inject")
                            }
                            val descFrom = findDesc(method)
                                ?: throw IllegalStateException("Cannot find desc for method ${method.name} at $targetClass")
                            val nameFrom = methodRef.removeSuffix("*")
                            mapMethodRef(
                                mapping,
                                targetClass,
                                classMappingEntry,
                                classRefmap,
                                methodRef,
                                nameFrom,
                                descFrom
                            )
                        } else {
                            val toFind = methodRef.removeSuffix("*")
                            val methodEntry = classMappingEntry.methodMapping.backingMap.find {
                                it.nameFrom == toFind
                            } ?: throw cannotFindMappingForMethodException(methodRef, targetClass)
                            val descFrom = methodEntry.desc
                            mapMethodRef(
                                mapping,
                                targetClass,
                                classMappingEntry,
                                classRefmap,
                                methodRef,
                                methodEntry.nameFrom,
                                descFrom
                            )
                        }
                    }
                }
            }
            remappingMethod = true
            methods.clear()


            if (remappingAtTargets) {
                atTargets.forEach { ref ->
                    fullMethodRefRegex.matchEntire(ref)?.let {
                        val ownerFrom = it.groupValues[1]
                        val nameFrom = it.groupValues[2]
                        val descFrom = it.groupValues[3]

                        val targetEntry = mapping[ownerFrom]
                            ?: throw IllegalStateException("Cannot find mapping for target class $ownerFrom at $targetClass")
                        val ownerTo = targetEntry.nameTo
                        val nameTo = targetEntry.methodMapping.getNameTo(nameFrom, descFrom)
                            ?: throw cannotFindMappingForMethodException("$nameFrom$descFrom", targetClass)
                        val descTo = mapping.remapDesc(descFrom)
                        classRefmap[ref] = "L$ownerTo;$nameTo$descTo"
                    } ?: fullFieldRefRegex.matchEntire(ref)?.let {
                        val ownerFrom = it.groupValues[1]
                        val nameFrom = it.groupValues[2]
                        val descFrom = it.groupValues[3]

                        val targetEntry = mapping[ownerFrom]
                            ?: throw IllegalStateException("Cannot find mapping for target class $ownerFrom at $targetClass")
                        val ownerTo = targetEntry.nameTo
                        val nameTo = targetEntry.fieldMapping.getNameTo(nameFrom)
                            ?: throw cannotFindMappingForFieldException(nameFrom, targetClass)
                        val descTo = mapping.remapDesc(descFrom)
                        classRefmap[ref] = "L$ownerTo;$nameTo:$descTo"
                    } ?: run {
                        val nameTo = mapping.getNameTo(ref.replace('.', '/'))
                        if (nameTo != null) {
                            classRefmap[ref] = nameTo
                        }
                    }
                }
            }
            remappingAtTargets = true
            atTargets.clear()
        }
    }

    private fun findDesc(method: MethodNode): String? {
        val parameters = descParamExtractRegex.matchEntire(method.desc)?.groupValues?.get(1) ?: return null
        val returnType = method.signature?.let {
            descReturnExtractRegex.matchEntire(it)?.groupValues?.get(1)?.replace(typeParameterRegex, "")
        } ?: "V"
        return "($parameters)$returnType"
    }

    private fun mapMethodRef(
        mapping: ClassMapping,
        targetClass: String,
        classMappingEntry: MappingEntry.Class,
        classRefmap: Object2ObjectOpenHashMap<String, String>,
        methodRef: String,
        nameFrom: String,
        descFrom0: String
    ) {
        var descFrom = descFrom0
        var nameTo: String?

        if (isInitMethod(nameFrom)) {
            nameTo = nameFrom
        } else {
            nameTo = classMappingEntry.methodMapping.getNameTo(nameFrom, descFrom)
            if (nameTo == null && descFrom.startsWith("()")) {
                val returnType = descFrom.substring(2)
                val candidates = classMappingEntry.methodMapping.backingMap.filter {
                    it.nameFrom == nameFrom && it.desc.endsWith(returnType)
                }
                if (candidates.size == 1) {
                    nameTo = candidates[0].nameTo
                    descFrom = candidates[0].desc
                }
            }
        }

        if (nameTo == null) {
            descFrom = mapToPrimitive(descFrom)
            nameTo = classMappingEntry.methodMapping.getNameTo(nameFrom, descFrom)
            if (nameTo == null && descFrom.startsWith("()")) {
                val returnType = descFrom.substring(2)
                val candidates = classMappingEntry.methodMapping.backingMap.filter {
                    it.nameFrom == nameFrom && it.desc.endsWith(returnType)
                }
                if (candidates.size == 1) {
                    nameTo = candidates[0].nameTo
                    descFrom = candidates[0].desc
                }
            }
        }
        if (nameTo == null) {
            throw cannotFindMappingForMethodException(methodRef, targetClass)
        }
        val descTo = mapping.remapDesc(descFrom)
        classRefmap[methodRef] = "L${classMappingEntry.nameTo};$nameTo$descTo"
    }

    private fun cannotFindMappingForMethodException(methodRef: String, targetClass: String): IllegalStateException {
        return IllegalStateException("Cannot find mapping for method $methodRef in $targetClass")
    }

    private fun cannotFindMappingForFieldException(fieldRef: String, targetClass: String): IllegalStateException {
        return IllegalStateException("Cannot find mapping for field $fieldRef in $targetClass")
    }

    private fun isInitMethod(nameFrom: String) = nameFrom == "<init>" || nameFrom == "<clinit>"

    private fun mapToPrimitive(desc: String): String {
        return desc.replace(javaLangRegex) {
            when (it.groupValues[1]) {
                "Byte" -> "B"
                "Character" -> "C"
                "Double" -> "D"
                "Float" -> "F"
                "Integer" -> "I"
                "Long" -> "J"
                "Short" -> "S"
                "Boolean" -> "Z"
                else -> it.value
            }
        }
    }

    private fun mapAccessors(
        mapping: ClassMapping,
        classNode: ClassNode,
        classMappingEntry: MappingEntry.Class,
        classRefmap: Object2ObjectOpenHashMap<String, String>
    ) {
        classNode.methods.asSequence().forEach method@{ methodNode ->
            methodNode.annotations.findAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;")?.let { annotationNode ->
                var nameFrom: String? = null
                annotationNode.values?.let {
                    for (i in 0 until it.size step 2) {
                        when (it[i] as String) {
                            "value" -> {
                                nameFrom = it[i + 1] as String
                            }
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
                    nameFrom = nameFrom!!.replaceFirstChar(Char::lowercaseChar)
                }
                val descFrom = methodNode.desc
                val nameTo = classMappingEntry.methodMapping.getNameTo(nameFrom!!, descFrom) ?: return@method
                val descTo = mapping.remapDesc(descFrom)
                classRefmap[nameFrom] = "$nameTo$descTo"
            } ?: methodNode.annotations.findAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;")
                ?.let { annotation ->
                    var nameFrom: String? = null
                    var remapping = true
                    annotation.visit {
                        visitValue<String>("value") {
                            nameFrom = it
                        }
                        visitValue<Boolean>("remap") {
                            remapping = it
                        }
                    }
                    if (!remapping) return@let
                    if (nameFrom == null) {
                        nameFrom = when {
                            methodNode.name.startsWith("get") -> {
                                methodNode.name.substring(3)
                            }
                            methodNode.name.startsWith("set") -> {
                                methodNode.name.substring(3)
                            }
                            else -> {
                                throw IllegalStateException("Cannot find name for accessor ${methodNode.name}")
                            }
                        }
                        nameFrom = nameFrom!!.replaceFirstChar(Char::lowercaseChar)
                    }
                    val descFrom = paramerterTypeDescRegex.matchEntire(methodNode.desc)?.groupValues?.get(1)
                        ?: returnTypeDescRegex.matchEntire(methodNode.desc)!!.groupValues[1]
                    val nameTo = classMappingEntry.fieldMapping.getNameTo(nameFrom!!)
                        ?: throw IllegalStateException("Cannot find mapping for accessor ${methodNode.name}")
                    val descTo = mapping.remapDesc(descFrom)
                    classRefmap[nameFrom] = "$nameTo:$descTo"
                }
        }
    }

    private companion object {
        val typeParameterRegex = "<.+>".toRegex()
        val javaLangRegex = "Ljava/lang/(.+?);".toRegex()
        val paramerterTypeDescRegex = "\\((.+)\\).+".toRegex()
        val returnTypeDescRegex = "\\(\\)(.+)".toRegex()
        val descParamExtractRegex =
            "\\((.*)Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo.*?;.*\\).+".toRegex()
        val descReturnExtractRegex =
            "\\(.*Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<(L.+;)>;.*\\).+".toRegex()
        val methodRefRegex = "([^;/\\s\\n]+)(\\(.*\\).+)".toRegex()
        val wildcardMethodRefRegex = "([^;/\\s\\n]+)\\*".toRegex()
        val fullMethodRefRegex = "L(.+);(.+)(\\(.*\\).+)".toRegex()
        val fullFieldRefRegex = "L(.+);(.+):(\\[*(?:L.+;|[BCDFIJSZ]))".toRegex()
    }
}