package dev.luna5ama.jartools.remap

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap

sealed interface MappingEntry {
    val nameFrom: String
    val desc: String
    val nameTo: String

    class Field(override val nameFrom: String, override val nameTo: String) : MappingEntry {
        override val desc: String
            get() = ""

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Field) return false

            if (nameFrom != other.nameFrom) return false
            if (nameTo != other.nameTo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = nameFrom.hashCode()
            result = 31 * result + nameTo.hashCode()
            return result
        }

        override fun toString(): String {
            return "($nameFrom->$nameTo)"
        }
    }

    class Method(override val nameFrom: String, override val desc: String, override val nameTo: String) : MappingEntry {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Method) return false

            if (nameFrom != other.nameFrom) return false
            if (desc != other.desc) return false
            if (nameTo != other.nameTo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = nameFrom.hashCode()
            result = 31 * result + desc.hashCode()
            result = 31 * result + nameTo.hashCode()
            return result
        }

        override fun toString(): String {
            return "($nameFrom$desc->$nameTo)"
        }
    }
}

interface ClassMapping {
    val nameFrom: String
    val nameTo: String

    val fieldMappingInternal: Map<String, MappingEntry.Field>
    val methodMappingInternal: Map<String, Map<String, MappingEntry.Method>>

    val fieldsInternal: List<MappingEntry.Field>
    val methodsInternal: List<MappingEntry.Method>

    val fields: List<MappingEntry.Field> get() = fieldsInternal
    val methods: List<MappingEntry.Method> get() = methodsInternal

    fun getMethod(name: String, desc: String): MappingEntry.Method?
    fun getMethodNameTo(name: String, desc: String): String?
    fun getField(name: String): MappingEntry.Field?
    fun getFieldNameTo(name: String): String?
    fun toMutable(): MutableClassMapping
}

private open class ClassMappingImpl(
    override val nameFrom: String,
    override val nameTo: String = nameFrom,
    override val fieldMappingInternal: Map<String, MappingEntry.Field> = hashMapOf(),
    override val methodMappingInternal: Map<String, Map<String, MappingEntry.Method>> = hashMapOf()
) : ClassMapping {
    override val fieldsInternal by lazy { fieldMappingInternal.values.toList() }
    override val methodsInternal by lazy { methodMappingInternal.values.flatMap { it.values } }

    override fun getMethod(name: String, desc: String): MappingEntry.Method? {
        return methodMappingInternal[name]?.get(desc)
    }

    override fun getMethodNameTo(name: String, desc: String): String? {
        return getMethod(name, desc)?.nameTo
    }

    override fun getField(name: String): MappingEntry.Field? {
        return fieldMappingInternal[name]
    }

    override fun getFieldNameTo(name: String): String? {
        return getField(name)?.nameTo
    }

    override fun toMutable(): MutableClassMapping {
        val methodsNew = HashMap<String, MutableMap<String, MappingEntry.Method>>()
        methodMappingInternal.forEach {
            methodsNew[it.key] = Object2ObjectArrayMap(it.value)
        }
        return MutableClassMappingImpl(
            nameFrom,
            nameTo,
            HashMap(fieldMappingInternal),
            methodsNew
        )
    }
}

interface MutableClassMapping : ClassMapping {
    override val fieldMappingInternal: MutableMap<String, MappingEntry.Field>
    override val methodMappingInternal: MutableMap<String, MutableMap<String, MappingEntry.Method>>

    override val fieldsInternal: MutableList<MappingEntry.Field> get() = fieldMappingInternal.values.toMutableList()
    override val methodsInternal: MutableList<MappingEntry.Method>
        get() = mutableListOf<MappingEntry.Method>().apply {
            methodMappingInternal.values.flatMapTo(this) { it.values }
        }

    fun addField(field: MappingEntry.Field)
    fun addMethod(method: MappingEntry.Method)
    fun addAll(other: ClassMapping)

    companion object {
        @JvmStatic
        operator fun invoke(name: String): MutableClassMapping {
            return MutableClassMappingImpl(name)
        }
    }
}

private class MutableClassMappingImpl(
    nameFrom: String,
    nameTo: String = nameFrom,
    override val fieldMappingInternal: MutableMap<String, MappingEntry.Field> = hashMapOf(),
    override val methodMappingInternal: MutableMap<String, MutableMap<String, MappingEntry.Method>> = hashMapOf()
) : ClassMappingImpl(nameFrom, nameTo, fieldMappingInternal, methodMappingInternal), MutableClassMapping {
    override val fieldsInternal = fieldMappingInternal.values.toMutableList()
    override val methodsInternal = mutableListOf<MappingEntry.Method>().apply {
        methodMappingInternal.values.flatMapTo(this) { it.values }
    }

    override fun addField(field: MappingEntry.Field) {
        fieldMappingInternal[field.nameFrom] = field
        fieldsInternal.add(field)
    }

    override fun addMethod(method: MappingEntry.Method) {
        val map = methodMappingInternal[method.nameFrom]
        if (map == null) {
            methodMappingInternal[method.nameFrom] = Object2ObjectArrayMap(arrayOf(method.desc), arrayOf(method), 1)
        } else {
            map[method.desc] = method
        }
        methodsInternal.add(method)
    }

    override fun addAll(other: ClassMapping) {
        fieldMappingInternal.putAll(other.fieldMappingInternal)
        other.methodMappingInternal.forEach {
            val map = methodMappingInternal[it.key]
            if (map == null) {
                methodMappingInternal[it.key] = Object2ObjectArrayMap(it.value)
            } else {
                map.putAll(it.value)
            }
        }
        fieldsInternal.addAll(other.fieldsInternal)
        methodsInternal.addAll(other.methodsInternal)
    }
}

typealias MutableMapping = MutableMap<String, MutableClassMapping>
typealias Mapping = Map<String, ClassMapping>

fun mergeMapping(a: MutableMapping, b: Mapping): MutableMapping {
    b.forEach {
        a.getOrPutEmpty(it.key).addAll(it.value)
    }
    return a
}

fun MutableMapping.getOrPutEmpty(name: String): MutableClassMapping {
    return getOrPut(name) { MutableClassMapping(name) }
}
