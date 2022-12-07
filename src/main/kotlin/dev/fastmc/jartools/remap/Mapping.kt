@file:Suppress("NOTHING_TO_INLINE", "DuplicatedCode", "unused")

package dev.fastmc.jartools.remap

import dev.fastmc.jartools.util.SelfHashMap
import it.unimi.dsi.fastutil.objects.*

sealed interface MappingEntryMap<T : MappingEntry> {
    val size: Int
    val entries: ObjectCollection<T>

    fun isEmpty(): Boolean
}

sealed interface MutableMappingEntryMap<T : MappingEntry> : MappingEntryMap<T> {
    fun add(entry: T)
    fun addAll(entries: ObjectCollection<T>)
    fun addAll(entries: MappingEntryMap<in T>)
}

class MutableMappingEntryMapImpl<T : MappingEntry> internal constructor() :
    MutableMappingEntryMap<T> {
    val backingMap = SelfHashMap<T>()

    override val size: Int
        get() = backingMap.size

    override val entries = backingMap.values

    override fun isEmpty(): Boolean {
        return backingMap.isEmpty()
    }

    inline fun get(hash: Int, compareFunc: (T) -> Boolean): T? {
        return backingMap.get(hash, compareFunc)
    }

    override fun add(entry: T) {
        backingMap.add(entry)
    }

    override fun addAll(entries: ObjectCollection<T>) {
        backingMap.putAll(entries)
    }

    override fun addAll(entries: MappingEntryMap<in T>) {
        @Suppress("UNCHECKED_CAST")
        backingMap.putAll((entries as MutableMappingEntryMapImpl<T>).backingMap)
    }

    override fun toString(): String {
        return entries.toString()
    }
}

typealias FieldMappingEntryMap = MappingEntryMap<MappingEntry.Field>
typealias MutableFieldMappingEntryMap = MutableMappingEntryMap<MappingEntry.Field>

inline fun FieldMappingEntryMap.get(nameFrom: String): MappingEntry.Field? {
    return (this as MutableMappingEntryMapImpl).get(MappingEntry.Field.hash(nameFrom)) { it.nameFrom == nameFrom }
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
    ) { it.nameFrom == nameFrom && it.desc == desc }
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

inline fun ClassMapping.get(nameFrom: String): MappingEntry.Class? {
    return (this as MutableMappingEntryMapImpl).get(MappingEntry.Class.hash(nameFrom)) { it.nameFrom == nameFrom }
}

@JvmName("classGetNameTo")
inline fun ClassMapping.getNameTo(nameFrom: String): String? {
    return get(nameFrom)?.nameTo
}

inline fun MutableClassMapping.get(nameFrom: String): MappingEntry.MutableClass? {
    return (this as MutableMappingEntryMapImpl).get(MappingEntry.Class.hash(nameFrom)) { it.nameFrom == nameFrom }
}

inline fun MutableClassMapping.getNameTo(nameFrom: String): String? {
    return get(nameFrom)?.nameTo
}

inline fun MutableClassMapping.getOrCreate(name: String): MappingEntry.MutableClass {
    val key = MappingEntry.Class.hash(name)
    var mapping = (this as MutableMappingEntryMapImpl).get(key) { it.nameFrom == name }
    if (mapping == null) {
        mapping = MappingEntry.MutableClass(name, name, key)
        add(mapping)
    }
    return mapping
}

inline fun MutableClassMapping.getOrCreate(nameFrom: String, nameTo: String): MappingEntry.MutableClass {
    val key = MappingEntry.Class.hash(nameFrom)
    var mapping = (this as MutableMappingEntryMapImpl).get(key) { it.nameFrom == nameFrom }
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
) : Comparable<MappingEntry> {
    constructor(nameFrom: String, nameTo: String) : this(nameFrom, nameTo, HashCache())

    constructor(nameFrom: String, nameTo: String, hash: Int) : this(nameFrom, nameTo, HashCache(hash))

    protected abstract fun hash(): Int

    override fun compareTo(other: MappingEntry): Int {
        var result = nameFrom.compareTo(other.nameFrom)
        if (result == 0) result = nameTo.compareTo(other.nameTo)
        return result
    }

    final override fun hashCode(): Int {
        if (!hashCache.hashInit) {
            hashCache.hash = hash()
        }
        return hashCache.hash
    }

    protected class HashCache {
        constructor()
        constructor(hash: Int) {
            this.hash = hash
            this.hashInit = true
        }

        var hashInit = false
            private set
        var hash = 0
            set(value) {
                field = value
                hashInit = true
            }
    }

    class Field : MappingEntry {
        constructor(nameFrom: String, nameTo: String) : super(nameFrom, nameTo)
        constructor(nameFrom: String, nameTo: String, hash: Int) : super(nameFrom, nameTo, hash)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Field) return false

            if (nameFrom != other.nameFrom) return false

            return true
        }

        override fun hash(): Int {
            return hash(nameFrom)
        }

        override fun toString(): String {
            return "($nameFrom->$nameTo)"
        }

        companion object {
            inline fun hash(nameFrom: String): Int {
                return nameFrom.hashCode()
            }
        }
    }

    class Method : MappingEntry {
        constructor(nameFrom: String, desc: String, nameTo: String) : super(nameFrom, nameTo) {
            this.desc = desc
        }

        constructor(nameFrom: String, desc: String, nameTo: String, hash: Int) : super(nameFrom, nameTo, hash) {
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

        override fun hash(): Int {
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
            inline fun hash(nameFrom: String, desc: String): Int {
                var result = nameFrom.hashCode()
                result = 31 * result + desc.hashCode()
                return result
            }
        }
    }

    sealed class Class : MappingEntry {
        constructor(nameFrom: String, nameTo: String) : super(nameFrom, nameTo)
        constructor(nameFrom: String, nameTo: String, hash: Int) : super(nameFrom, nameTo, hash)

        abstract val fieldMapping: FieldMappingEntryMap
        abstract val methodMapping: MethodMappingEntryMap

        abstract fun toMutable(): MutableClass

        companion object {
            inline fun hash(nameFrom: String): Int {
                return nameFrom.hashCode()
            }
        }
    }

    sealed class MutableClass : Class {
        constructor(nameFrom: String, nameTo: String) : super(nameFrom, nameTo)
        constructor(nameFrom: String, nameTo: String, hash: Int) : super(nameFrom, nameTo, hash)

        abstract override val fieldMapping: MutableFieldMappingEntryMap
        abstract override val methodMapping: MutableMethodMappingEntryMap

        companion object {
            @JvmStatic
            operator fun invoke(nameFrom: String, nameTo: String): MutableClass {
                return MutableClassImpl(nameFrom, nameTo)
            }

            @JvmStatic
            operator fun invoke(nameFrom: String, nameTo: String, hash: Int): MutableClass {
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
    ) : MutableClass(nameFrom, nameTo, hashCache.hash) {
        private constructor(nameFrom: String, nameTo: String, hashCache: HashCache) : this(
            nameFrom,
            nameTo,
            MutableMappingEntryMapImpl(),
            MutableMappingEntryMapImpl(),
            hashCache
        )

        constructor(nameFrom: String, nameTo: String) : this(nameFrom, nameTo, HashCache())
        constructor(nameFrom: String, nameTo: String, hash: Int) : this(nameFrom, nameTo, HashCache(hash))


        override fun toMutable(): MutableClass {
            return MutableClassImpl(nameFrom, nameTo, fieldMapping, methodMapping, hashCache)
        }

        override fun hash(): Int {
            return hash(nameFrom)
        }
    }
}
