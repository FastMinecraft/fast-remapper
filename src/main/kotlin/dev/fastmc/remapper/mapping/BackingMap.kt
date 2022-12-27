package dev.fastmc.remapper.mapping

import it.unimi.dsi.fastutil.HashCommon
import it.unimi.dsi.fastutil.objects.ObjectIterator
import java.util.function.Consumer

interface LongHashCode {
    fun hashCodeLong(): Long
}

class BackingMap<T : LongHashCode> : MutableCollection<T> {
    var bucketA = arrayOfNulls<Any>(16)
    var bucketB = arrayOfNulls<Any>(16)
    private val buckets = arrayOf(bucketA, bucketB)
    var capacity = 16
    private var mask = 15
    private var bits = 4
    private var rehashSize = 8
    private var size0 = 0

    override val size get() = size0

    private fun rehash(shift: Int) {
        val oldBucketA = bucketA
        val oldBucketB = bucketB
        val oldCapacity = capacity
        val oldSize = size0

        try {
            capacity = capacity shl shift
            mask = capacity - 1
            bits++
            rehashSize = rehashSize shl shift
            size0 = 0

            bucketA = arrayOfNulls(capacity)
            bucketB = arrayOfNulls(capacity)
            buckets[0] = bucketA
            buckets[1] = bucketB

            for (i in 0 until oldCapacity) {
                val a = oldBucketA[i]
                val b = oldBucketB[i]
                if (a != null) {
                    @Suppress("UNCHECKED_CAST")
                    rehashAdd(a as T)
                }
                if (b != null) {
                    @Suppress("UNCHECKED_CAST")
                    rehashAdd(b as T)
                }
            }
        } catch (e: RehashException) {
            while (true) {
                try {
                    capacity = capacity shl 1
                    mask = capacity - 1
                    bits++
                    rehashSize = rehashSize shl 1
                    size0 = 0

                    bucketA = arrayOfNulls(capacity)
                    bucketB = arrayOfNulls(capacity)
                    buckets[0] = bucketA
                    buckets[1] = bucketB

                    for (i in 0 until oldCapacity) {
                        val a = oldBucketA[i]
                        val b = oldBucketB[i]
                        if (a != null) {
                            @Suppress("UNCHECKED_CAST")
                            rehashAdd(a as T)
                        }
                        if (b != null) {
                            @Suppress("UNCHECKED_CAST")
                            rehashAdd(b as T)
                        }
                    }

                    break
                } catch (e: RehashException) {
                    continue
                }
            }
        }

        assert(oldSize == size0) { "Size changed during rehash" }
    }

    private fun rehashAdd(entry: T): Boolean {
        val hashCode = entry.hashCodeLong()
        return rehashAdd(0, 64 - bits, entry, hashCode, hashCode)
    }

    private fun rehashAdd(
        bucketIndex: Int,
        remainBits: Int,
        entry: T,
        hashCode: Long,
        rawHashCode: Long
    ): Boolean {
        val index = HashCommon.mix(hashCode).toInt() and (capacity - 1)

        val bucket = buckets[bucketIndex]
        val e = bucket[index]
        if (e == null) {
            bucket[index] = entry
            size0++
            return true
        }

        @Suppress("UNCHECKED_CAST")
        e as T
        val otherHash = e.hashCodeLong()
        if (otherHash != rawHashCode) {
            if (remainBits != 0) {
                rehashAdd(bucketIndex xor 1, remainBits - 1, entry, hashCode ushr 1, rawHashCode)
            } else {
                throw RehashException
            }
        } else {
            assert(e == entry) { "Hash collision: $e, $entry" }
        }

        return false
    }

    private fun internalAdd(entry: T): Boolean {
        val hashCode = entry.hashCodeLong()
        return internalAdd(0, 64 - bits, entry, hashCode, hashCode)
    }

    private fun internalAdd(
        bucketIndex: Int,
        remainBits: Int,
        entry: T,
        hashCode: Long,
        rawHashCode: Long
    ): Boolean {
        val index = HashCommon.mix(hashCode).toInt() and (capacity - 1)

        val bucket = buckets[bucketIndex]
        val e = bucket[index]
        if (e == null) {
            bucket[index] = entry
            size0++
            return true
        }

        @Suppress("UNCHECKED_CAST")
        e as T
        val otherHash = e.hashCodeLong()
        if (otherHash != rawHashCode) {
            if (remainBits != 0) {
                internalAdd(bucketIndex xor 1, remainBits - 1, entry, hashCode ushr 1, rawHashCode)
            } else {
                rehash(1)
            }
        } else {
            assert(e == entry) { "Hash collision: $e, $entry" }
        }

        return false
    }

    private fun internalGet(hashCode: Long): T? {
        return internalGet(0, 64 - bits, hashCode, hashCode)
    }

    private fun internalGet(bucketIndex: Int, remainBits: Int, hashCode: Long, rawHashCode: Long): T? {
        val index = HashCommon.mix(hashCode).toInt() and (capacity - 1)

        val e = buckets[bucketIndex][index]
        if (e == null) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        e as T
        val otherHash = e.hashCodeLong()
        if (otherHash == rawHashCode) {
            return e
        }

        if (remainBits != 0) {
            return internalGet(bucketIndex xor 1, remainBits - 1, hashCode ushr 1, rawHashCode)
        }

        return null
    }

    private fun internalFind(hashCode: Long): Int {
        return internalFind(0, 64 - bits, hashCode, hashCode)
    }

    private fun internalFind(bucketIndex: Int, remainBits: Int, hashCode: Long, rawHashCode: Long): Int {
        val index = HashCommon.mix(hashCode).toInt() and (capacity - 1)

        val e = buckets[bucketIndex][index]
        if (e == null) {
            return -1
        }

        @Suppress("UNCHECKED_CAST")
        e as T
        val otherHash = e.hashCodeLong()
        if (otherHash == rawHashCode) {
            return (bucketIndex shl 31) or index
        }

        if (remainBits != 0) {
            return internalFind(bucketIndex xor 1, remainBits - 1, hashCode ushr 1, rawHashCode)
        }

        return -1
    }

    fun ensureCapacity(newCapacity: Int) {
        if (newCapacity > rehashSize) {
            rehash(resizeShift(newCapacity) - bits)
        }
    }

    private fun resizeShift(i: Int): Int {
        var j = i - 1
        j = j or (j shr 1)
        j = j or (j shr 2)
        j = j or (j shr 4)
        j = j or (j shr 8)
        j = j or (j shr 16)
        return j.countOneBits()
    }

    override fun add(element: T): Boolean {
        if (size0 >= rehashSize) {
            rehash(1)
        }
        return internalAdd(element)
    }

    fun get(hashCode: Long): T? {
        return internalGet(hashCode)
    }

    override fun contains(element: T): Boolean {
        val result = internalGet(element.hashCodeLong())
        assert(result != null && result == element) { "Hash collision: $result, $element" }
        return result != null
    }

    fun addAll(other: BackingMap<T>) {
        ensureCapacity(size0 + other.size0)
        for (i in 0 until other.capacity) {
            val a = other.bucketA[i]
            val b = other.bucketB[i]
            if (a != null) {
                @Suppress("UNCHECKED_CAST")
                add(a as T)
            }
            if (b != null) {
                @Suppress("UNCHECKED_CAST")
                add(b as T)
            }
        }
    }

    override fun addAll(elements: Collection<T>): Boolean {
        var result = false
        ensureCapacity(size0 + elements.size)
        for (entry in elements) {
            result = add(entry) || result
        }
        return result
    }

    override fun clear() {
        size0 = 0
        bucketA.fill(null)
        bucketB.fill(null)
    }

    override fun isEmpty(): Boolean {
        return size0 == 0
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        for (entry in elements) {
            if (!contains(entry)) {
                return false
            }
        }
        return true
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        var result = false
        for (i in 0 until capacity) {
            val a = bucketA[i]
            val b = bucketB[i]
            if (a != null) {
                @Suppress("UNCHECKED_CAST")
                if (!elements.contains(a as T)) {
                    bucketA[i] = null
                    size0--
                    result = true
                }
            }
            if (b != null) {
                @Suppress("UNCHECKED_CAST")
                if (!elements.contains(b as T)) {
                    bucketB[i] = null
                    size0--
                    result = true
                }
            }
        }
        return result
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var result = false
        for (entry in elements) {
            result = remove(entry) || result
        }
        return result
    }

    override fun remove(element: T): Boolean {
        val index = internalFind(element.hashCodeLong())
        if (index == -1) {
            return false
        }
        buckets[index ushr 31][index and Int.MAX_VALUE] = null
        return true
    }

    inline fun <reified T> toTArray(): Array<T> {
        val array = arrayOfNulls<T>(size)
        var index = 0
        for (i in 0 until capacity) {
            val a = bucketA[i]
            val b = bucketB[i]
            if (a != null) {
                array[index++] = a as T
            }
            if (b != null) {
                array[index++] = b as T
            }
        }
        @Suppress("UNCHECKED_CAST")
        return array as Array<T>
    }

    override fun iterator(): MutableIterator<T> {
        return BackingMapIterator()
    }

    inline fun forEachFast(action: (T) -> Unit) {
        for (i in 0 until capacity) {
            val a = bucketA[i]
            val b = bucketB[i]
            if (a != null) {
                @Suppress("UNCHECKED_CAST")
                action(a as T)
            }
            if (b != null) {
                @Suppress("UNCHECKED_CAST")
                action(b as T)
            }
        }
    }

    override fun forEach(action: Consumer<in T>) {
        for (i in 0 until capacity) {
            val a = bucketA[i]
            val b = bucketB[i]
            if (a != null) {
                @Suppress("UNCHECKED_CAST")
                (action.accept(a as T))
            }
            if (b != null) {
                @Suppress("UNCHECKED_CAST")
                (action.accept(b as T))
            }
        }
    }

    private inner class BackingMapIterator : ObjectIterator<T> {
        var index = 0

        init {
            skip()
        }

        override fun remove() {
            buckets[index and 1][index shr 1] = null
        }

        override fun hasNext(): Boolean {
            return index < capacity * 2
        }

        override fun next(): T {
            @Suppress("UNCHECKED_CAST") val result = buckets[index and 1][index shr 1] as T
            index++
            skip()
            return result
        }

        private fun skip() {
            while (index < capacity * 2) {
                val bucket = buckets[index and 1]
                val e = bucket[index shr 1]
                if (e != null) {
                    return
                }
                index++
            }
        }
    }

    private object RehashException : RuntimeException()
}