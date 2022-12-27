@file:Suppress("NOTHING_TO_INLINE", "DuplicatedCode", "unused")

package dev.fastmc.remapper.mapping

import it.unimi.dsi.fastutil.objects.*

sealed interface MappingEntryMap<T : MappingEntry> {
    val backingMap: BackingMap<T>
    val size: Int

    fun isEmpty(): Boolean
}

sealed interface MutableMappingEntryMap<T : MappingEntry> : MappingEntryMap<T> {
    fun add(entry: T)
    fun addAll(entries: ObjectCollection<T>)
    fun addAll(entries: MappingEntryMap<in T>)
}

class MutableMappingEntryMapImpl<T : MappingEntry> internal constructor() :
    MutableMappingEntryMap<T> {
    override val backingMap = BackingMap<T>()

    override val size: Int
        get() = backingMap.size

    override fun isEmpty(): Boolean {
        return backingMap.isEmpty()
    }

    inline fun get(hash: Long): T? {
        return backingMap.get(hash)
    }

    override fun add(entry: T) {
        backingMap.add(entry)
    }

    override fun addAll(entries: ObjectCollection<T>) {
        backingMap.addAll(entries)
    }

    override fun addAll(entries: MappingEntryMap<in T>) {
        @Suppress("UNCHECKED_CAST")
        backingMap.addAll(entries.backingMap as BackingMap<T>)
    }
}

typealias FieldMappingEntryMap = MappingEntryMap<MappingEntry.Field>
typealias MutableFieldMappingEntryMap = MutableMappingEntryMap<MappingEntry.Field>

inline fun FieldMappingEntryMap.get(nameFrom: String): MappingEntry.Field? {
    return (this as MutableMappingEntryMapImpl).get(MappingEntry.Field.hash(nameFrom))
}

@JvmName("fieldGetNameTo")
inline fun FieldMappingEntryMap.getNameTo(nameFrom: String): String? {
    return get(nameFrom)?.nameTo
}

typealias MethodMappingEntryMap = MappingEntryMap<MappingEntry.Method>
typealias MutableMethodMappingEntryMap = MutableMappingEntryMap<MappingEntry.Method>

inline fun MethodMappingEntryMap.get(nameFrom: String, desc: String): MappingEntry.Method? {
    return (this as MutableMappingEntryMapImpl).get(
        MappingEntry.Method.hash(
            nameFrom,
            desc
        )
    )
}

inline fun MethodMappingEntryMap.getNameTo(nameFrom: String, desc: String): String? {
    return get(nameFrom, desc)?.nameTo
}

typealias ClassMapping = MappingEntryMap<MappingEntry.Class>
typealias MutableClassMapping = MutableMappingEntryMap<MappingEntry.MutableClass>

fun MutableClassMapping(): MutableClassMapping {
    return MutableMappingEntryMapImpl()
}

inline fun MutableClassMapping.asImmutable(): ClassMapping {
    @Suppress("UNCHECKED_CAST")
    return this as ClassMapping
}

inline operator fun ClassMapping.get(nameFrom: String): MappingEntry.Class? {
    return (this as MutableMappingEntryMapImpl).get(MappingEntry.Class.hash(nameFrom))
}

@JvmName("classGetNameTo")
inline fun ClassMapping.getNameTo(nameFrom: String): String? {
    return get(nameFrom)?.nameTo
}

inline fun MutableClassMapping.get(nameFrom: String): MappingEntry.MutableClass? {
    return (this as MutableMappingEntryMapImpl).get(MappingEntry.Class.hash(nameFrom))
}

inline fun MutableClassMapping.getNameTo(nameFrom: String): String? {
    return get(nameFrom)?.nameTo
}

inline fun MutableClassMapping.getOrCreate(name: String): MappingEntry.MutableClass {
    val key = MappingEntry.Class.hash(name)
    var mapping = (this as MutableMappingEntryMapImpl).get(key)
    if (mapping == null) {
        mapping = MappingEntry.MutableClass(name, name, key)
        add(mapping)
    }
    return mapping
}

inline fun MutableClassMapping.getOrCreate(nameFrom: String, nameTo: String): MappingEntry.MutableClass {
    val key = MappingEntry.Class.hash(nameFrom)
    var mapping = (this as MutableMappingEntryMapImpl).get(key)
    if (mapping == null) {
        mapping = MappingEntry.MutableClass(nameFrom, nameTo, key)
        add(mapping)
    }
    return mapping
}

@Suppress("EqualsOrHashCode")
sealed class MappingEntry constructor(
    val nameFrom: String,
    val nameTo: String,
    protected val hashCache: HashCache
) : Comparable<MappingEntry>, LongHashCode {
    constructor(nameFrom: String, nameTo: String) : this(nameFrom, nameTo, HashCache())

    constructor(nameFrom: String, nameTo: String, hash: Long) : this(nameFrom, nameTo, HashCache(hash))

    protected abstract fun hash(): Long

    override fun compareTo(other: MappingEntry): Int {
        var result = nameFrom.compareTo(other.nameFrom)
        if (result == 0) result = nameTo.compareTo(other.nameTo)
        return result
    }

    final override fun hashCodeLong(): Long {
        if (!hashCache.hashInit) {
            hashCache.hash = hash()
        }
        return hashCache.hash
    }

    final override fun hashCode(): Int {
        val hash = hashCodeLong()
        return ((hash ushr 32) xor hash).toInt()
    }

    protected class HashCache {
        constructor()
        constructor(hash: Long) {
            this.hash = hash
            this.hashInit = true
        }

        var hashInit = false
            private set
        var hash = 0L
            set(value) {
                field = value
                hashInit = true
            }
    }

    class Field : MappingEntry {
        constructor(nameFrom: String, nameTo: String) : super(nameFrom, nameTo)
        constructor(nameFrom: String, nameTo: String, hash: Long) : super(nameFrom, nameTo, hash)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Field) return false

            if (nameFrom != other.nameFrom) return false

            return true
        }

        override fun hash(): Long {
            return hash(nameFrom)
        }

        override fun toString(): String {
            return "($nameFrom->$nameTo)"
        }

        companion object {
            inline fun hash(nameFrom: String): Long {
                if (nameFrom.isEmpty()) return 0L
                var result = nameFrom[0].code shl 8
                result = 31 * result + nameFrom[nameFrom.length - 1].code
                result = 31 * result + nameFrom.length
                return (result.toLong() shl 32) + nameFrom.hashCode().toLong()
            }
        }
    }

    class Method : MappingEntry {
        constructor(nameFrom: String, desc: String, nameTo: String) : super(nameFrom, nameTo) {
            this.desc = desc
        }

        constructor(nameFrom: String, desc: String, nameTo: String, hash: Long) : super(nameFrom, nameTo, hash) {
            this.desc = desc
        }

        val desc: String

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Method) return false

            if (nameFrom != other.nameFrom) return false
            if (desc != other.desc) return false

            return true
        }

        override fun hash(): Long {
            return hash(nameFrom, desc)
        }

        override fun compareTo(other: MappingEntry): Int {
            var result = super.compareTo(other)
            if (other !is Method) return result
            if (result == 0) result = desc.compareTo(other.desc)
            return result
        }

        override fun toString(): String {
            return "($nameFrom$desc->$nameTo)"
        }

        companion object {
            inline fun hash(nameFrom: String, desc: String): Long {
                var a = nameFrom[0].code shl 8
                a = 31 * a + nameFrom.length
                a = 31 * a + nameFrom[nameFrom.length - 1].code
                var b = desc[0].code shl 8
                b = 31 * b + desc.length
                b = 31 * b + desc[desc.length - 1].code
                val c = 0x111L * nameFrom.hashCode().toLong() + desc.hashCode().toLong()
                return (a.toLong() * 0x10101 + b.toLong()) * 0x10101 + c
            }
        }
    }

    sealed class Class(nameFrom: String, nameTo: String, hashCache: HashCache) :
        MappingEntry(nameFrom, nameTo, hashCache) {

        abstract val fieldMapping: FieldMappingEntryMap
        abstract val methodMapping: MethodMappingEntryMap

        abstract fun toMutable(): MutableClass

        companion object {
            inline fun hash(nameFrom: String): Long {
                if (nameFrom.isEmpty()) return 0L
                var result = nameFrom[0].code shl 8
                result = 31 * result + nameFrom[nameFrom.length - 1].code
                result = 31 * result + nameFrom.length
                return (result.toLong() shl 32) or nameFrom.hashCode().toLong()
            }
        }
    }

    sealed class MutableClass(nameFrom: String, nameTo: String, hashCache: HashCache) :
        Class(nameFrom, nameTo, hashCache) {

        abstract override val fieldMapping: MutableFieldMappingEntryMap
        abstract override val methodMapping: MutableMethodMappingEntryMap

        companion object {
            @JvmStatic
            operator fun invoke(nameFrom: String, nameTo: String): MutableClass {
                return MutableClassImpl(nameFrom, nameTo)
            }

            @JvmStatic
            operator fun invoke(nameFrom: String, nameTo: String, hash: Long): MutableClass {
                return MutableClassImpl(nameFrom, nameTo, hash)
            }
        }
    }

    private class MutableClassImpl private constructor(
        nameFrom: String,
        nameTo: String,
        override val fieldMapping: MutableFieldMappingEntryMap,
        override val methodMapping: MutableMethodMappingEntryMap,
        hashCache: HashCache
    ) : MutableClass(nameFrom, nameTo, hashCache) {
        constructor(nameFrom: String, nameTo: String, hash: Long) : this(
            nameFrom,
            nameTo,
            MutableMappingEntryMapImpl(),
            MutableMappingEntryMapImpl(),
            HashCache(hash)
        )

        constructor(nameFrom: String, nameTo: String) : this(
            nameFrom,
            nameTo,
            MutableMappingEntryMapImpl(),
            MutableMappingEntryMapImpl(),
            HashCache()
        )

        override fun toMutable(): MutableClass {
            return MutableClassImpl(nameFrom, nameTo, fieldMapping, methodMapping, hashCache)
        }

        override fun hash(): Long {
            return hash(nameFrom)
        }
    }
}


fun ClassMapping.reversed(): ClassMapping {
    val result = MutableClassMapping()

    this.backingMap.forEachFast { c ->
        val classEntry = MappingEntry.MutableClass(c.nameTo, c.nameFrom)
        result.add(classEntry)

        c.fieldMapping.backingMap.forEachFast { fieldEntry ->
            classEntry.fieldMapping.add(MappingEntry.Field(fieldEntry.nameTo, fieldEntry.nameFrom))
        }

        c.methodMapping.backingMap.forEachFast { methodEntry ->
            classEntry.methodMapping.add(MappingEntry.Method(methodEntry.nameTo, this.remapDesc(methodEntry.desc), methodEntry.nameFrom))
        }
    }

    return result.asImmutable()
}

private val objectTypeDecsRegex = "(?<=L)[^;]+(?=;)".toRegex()

fun ClassMapping.remapDesc(desc: String): String {
    return desc.replace(objectTypeDecsRegex) {
        this.getNameTo(it.value) ?: it.value
    }
}

fun ClassMapping.mapWith(other: ClassMapping): ClassMapping {
    val result = MutableClassMapping()

    this.backingMap.forEachFast { c ->
        val classEntry = MappingEntry.MutableClass(c.nameFrom, other.getNameTo(c.nameTo)!!)
        result.add(classEntry)

        c.fieldMapping.backingMap.forEachFast { fieldEntry ->
            classEntry.fieldMapping.add(MappingEntry.Field(fieldEntry.nameFrom, other.getNameTo(fieldEntry.nameTo)!!))
        }

        c.methodMapping.backingMap.forEachFast { methodEntry ->
            classEntry.methodMapping.add(
                MappingEntry.Method(
                    methodEntry.nameFrom,
                    this.remapDesc(methodEntry.desc),
                    other.getNameTo(methodEntry.nameTo)!!
                )
            )
        }
    }

    return result.asImmutable()
}