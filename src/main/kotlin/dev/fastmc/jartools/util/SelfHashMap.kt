/*
 * Copyright (C) 2002-2022 Sebastiano Vigna
 * Modifications copyright (C) 2022 Luna5ama
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fastmc.jartools.util

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.HashCommon
import it.unimi.dsi.fastutil.Pair
import it.unimi.dsi.fastutil.objects.*
import java.util.*
import java.util.function.BiFunction
import java.util.function.Consumer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Adapted from it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
@Suppress(
    "unused", "UNCHECKED_CAST", "ConvertTwoComparisonsToRangeCheck", "EqualsOrHashCode",
    "KDocUnresolvedReference", "DuplicatedCode", "NOTHING_TO_INLINE"
)
class SelfHashMap<T : Any> @JvmOverloads constructor(
    expected: Int = Hash.DEFAULT_INITIAL_SIZE,
    f: Float = Hash.DEFAULT_LOAD_FACTOR
) : AbstractObject2ObjectMap<T, T>(), Cloneable, Hash {
    /**
     * The array of keys.
     */
    var key: Array<Any?>

    /**
     * The mask for wrapping a position counter.
     */
    var mask: Int

    /**
     * The current table size.
     */
    private var n: Int

    /**
     * Threshold after which we rehash. It must be the table size times [.f].
     */
    private var maxFill: Int

    /**
     * We never resize below this threshold, which is the construction-time {#n}.
     */
    private val minN: Int

    /**
     * Number of entries in the set (including the key zero, if present).
     */
    private var size0 = 0

    /**
     * The acceptable load factor.
     */
    private val f: Float

    /**
     * Cached set of entries.
     */
    private var entries0: Object2ObjectMap.FastEntrySet<T, T>? = null

    /**
     * Cached set of keys.
     */
    private var keys0: ObjectSet<T>? = null

    /**
     * Creates a new hash map.
     *
     *
     *
     * The actual table size will be the least power of two greater than `expected`/`f`.
     *
     * @param expected the expected number of elements in the hash map.
     * @param f        the load factor.
     */
    /**
     * Creates a new hash map with initial expected [Hash.DEFAULT_INITIAL_SIZE] entries and
     * [Hash.DEFAULT_LOAD_FACTOR] as load factor.
     */
    /**
     * Creates a new hash map with [Hash.DEFAULT_LOAD_FACTOR] as load factor.
     *
     * @param expected the expected number of elements in the hash map.
     */
    init {
        require(!(f <= 0 || f >= 1)) { "Load factor must be greater than 0 and smaller than 1" }
        require(expected >= 0) { "The expected number of elements must be non-negative" }
        this.f = f
        n = HashCommon.arraySize(expected, f)
        minN = n
        mask = n - 1
        maxFill = HashCommon.maxFill(n, f)
        key = arrayOfNulls(n + 1)
    }
    /**
     * Creates a new hash map copying a given one.
     *
     * @param m a [Map] to be copied into the new hash map.
     * @param f the load factor.
     */
    /**
     * Creates a new hash map with [Hash.DEFAULT_LOAD_FACTOR] as load factor copying a given one.
     *
     * @param m a [Map] to be copied into the new hash map.
     */
    @JvmOverloads
    constructor(m: Map<T, T>, f: Float = Hash.DEFAULT_LOAD_FACTOR) : this(m.size, f) {
        putAll(m)
    }
    /**
     * Creates a new hash map copying a given type-specific one.
     *
     * @param m a type-specific map to be copied into the new hash map.
     * @param f the load factor.
     */
    /**
     * Creates a new hash map with [Hash.DEFAULT_LOAD_FACTOR] as load factor copying a given
     * type-specific one.
     *
     * @param m a type-specific map to be copied into the new hash map.
     */
    @JvmOverloads
    constructor(m: Object2ObjectMap<T, T>, f: Float = Hash.DEFAULT_LOAD_FACTOR) : this(m.size, f) {
        putAll(m as Map<T, T>)
    }
    /**
     * Creates a new hash map using the elements of two parallel arrays.
     *
     * @param k the array of keys of the new hash map.
     * @param v the array of corresponding values in the new hash map.
     * @param f the load factor.
     * @throws IllegalArgumentException if `k` and `v` have different lengths.
     */
    /**
     * Creates a new hash map with [Hash.DEFAULT_LOAD_FACTOR] as load factor using the elements of
     * two parallel arrays.
     *
     * @param k the array of keys of the new hash map.
     * @param v the array of corresponding values in the new hash map.
     * @throws IllegalArgumentException if `k` and `v` have different lengths.
     */
    @JvmOverloads
    constructor(k: Array<T>, v: Array<T>, f: Float = Hash.DEFAULT_LOAD_FACTOR) : this(k.size, f) {
        require(k.size == v.size) { "The key array and the value array have different lengths (" + k.size + " and " + v.size + ")" }
        for (i in k.indices) this[k[i]] = v[i]
    }

    private fun ensureCapacity(capacity: Int) {
        val needed = HashCommon.arraySize(capacity, f)
        if (needed > n) rehash(needed)
    }

    private fun tryCapacity(capacity: Long) {
        val needed = min(
            (1 shl 30).toLong(),
            max(2, HashCommon.nextPowerOfTwo(ceil((capacity / f).toDouble()).toLong()))
        ).toInt()
        if (needed > n) rehash(needed)
    }

    private fun removeEntry(pos: Int): T {
        val oldValue = key[pos] as T
        key[pos] = null
        size0--
        shiftKeys(pos)
        if (n > minN && size0 < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) rehash(n / 2)
        return oldValue
    }

    fun putAll(from: ObjectCollection<T>) {
        if (f <= .5) ensureCapacity(from.size) // The resulting map will be sized for m.size() elements
        else tryCapacity((size + from.size).toLong()) // The resulting map will be tentatively sized for size() + m.size()
        // elements
        for (e in from) {
            add(e)
        }
    }

    fun putAll(from: SelfHashMap<T>) {
        if (f <= .5) ensureCapacity(from.size) // The resulting map will be sized for m.size() elements
        else tryCapacity((size + from.size).toLong()) // The resulting map will be tentatively sized for size() + m.size()
        // elements
        for (i in from.key.indices) {
            val k = from.key[i]
            if (k != null) add(k as T)
        }
    }

    override fun putAll(from: Map<out T, T>) {
        if (f <= .5) ensureCapacity(from.size) // The resulting map will be sized for m.size() elements
        else tryCapacity((size + from.size).toLong()) // The resulting map will be tentatively sized for size() + m.size()
        // elements
        super.putAll(from)
    }

    private inline fun find(k: T): Int {
        var curr: T?
        var pos: Int
        // The starting point.
        if (key[(HashCommon.mix(k.hashCode()) and mask).also { pos = it }].also {
                curr = it as T?
            } == null) return -(pos + 1)
        if (k == curr) return pos
        // There's always an unused entry.
        while (true) {
            if (key[(pos + 1 and mask).also { pos = it }].also { curr = it as T? } == null) return -(pos + 1)
            if (k == curr) return pos
        }
    }

    private inline fun insert(pos: Int, k: T) {
        key[pos] = k
        if (size0++ >= maxFill) {
            rehash(HashCommon.arraySize(size0 + 1, f))
        }
    }

    fun add(key: T): T? {
        val pos = find(key)
        if (pos < 0) {
            insert(-pos - 1, key)
            return null
        }
        val oldValue = this.key[pos] as T
        this.key[pos] = key
        return oldValue
    }

    override fun put(key: T, value: T): T? {
        check(key === value)
        val pos = find(key)
        if (pos < 0) {
            check(key === value)
            insert(-pos - 1, key)
            return null
        }
        val oldValue = this.key[pos] as T
        this.key[pos] = value
        return oldValue
    }

    /**
     * Shifts left entries with the specified hash code, starting at the specified position, and empties
     * the resulting free entry.
     *
     * @param pos a starting position.
     */
    @Suppress("NAME_SHADOWING")
    private fun shiftKeys(pos: Int) {
        // Shift entries with the same hash.
        var pos = pos
        var last: Int
        var slot: Int
        var curr: T?
        while (true) {
            pos = pos.also { last = it } + 1 and mask
            while (true) {
                if ((key[pos] as T?).also { curr = it } == null) {
                    key[last] = null
                    return
                }
                slot = HashCommon.mix(curr.hashCode()) and mask
                if (if (last <= pos) last >= slot || slot > pos else last >= slot && slot > pos) break
                pos = pos + 1 and mask
            }
            key[last] = curr
        }
    }

    override fun remove(key: T): T? {
        var curr: T?
        var pos: Int
        // The starting point.
        if ((this.key[(HashCommon.mix(key.hashCode()) and mask).also { pos = it }] as T?).also {
                curr = it
            } == null) return null
        if (key == curr) return removeEntry(pos)
        while (true) {
            if ((this.key[(pos + 1 and mask).also { pos = it }] as T?).also { curr = it } == null) return null
            if (key == curr) return removeEntry(pos)
        }
    }

    override operator fun get(key: T): T? {
        var curr: T?
        var pos: Int
        // The starting point.
        if ((this.key[(HashCommon.mix(key.hashCode()) and mask).also { pos = it }] as T?).also {
                curr = it
            } == null) return null
        if (key == curr) return this.key[pos] as T
        // There's always an unused entry.
        while (true) {
            if ((this.key[(pos + 1 and mask).also { pos = it }] as T?).also { curr = it } == null) return null
            if (key == curr) return this.key[pos] as T
        }
    }

    inline fun get(hash: Int, compareFunc: (T) -> Boolean): T? {
        var curr: T?
        var pos: Int
        // The starting point.
        if ((this.key[(HashCommon.mix(hash) and mask).also { pos = it }] as T?).also {
                curr = it
            } == null) return null
        if (compareFunc(curr!!)) return this.key[pos] as T
        // There's always an unused entry.
        while (true) {
            if ((this.key[(pos + 1 and mask).also { pos = it }] as T?).also {
                    curr = it
                } == null) return null
            if (compareFunc(curr!!)) return this.key[pos] as T
        }
    }

    override fun containsKey(key: T): Boolean {
        var curr: T?
        var pos: Int
        // The starting point.
        if ((this.key[(HashCommon.mix(key.hashCode()) and mask).also { pos = it }] as T?).also {
                curr = it
            } == null) return false
        if (key == curr) return true
        // There's always an unused entry.
        while (true) {
            if ((this.key[(pos + 1 and mask).also { pos = it }] as T?).also { curr = it } == null) return false
            if (key == curr) return true
        }
    }

    override fun containsValue(value: T): Boolean {
        var i = n
        while (i-- != 0) {
            if (key[i] != null && key[i] == value) return true
        }
        return false
    }

    /**
     * {@inheritDoc}
     */
    override fun getOrDefault(key: T, defaultValue: T): T {
        check(key === defaultValue)
        var curr: T?
        var pos: Int
        // The starting point.
        if ((this.key[(HashCommon.mix(key.hashCode()) and mask).also { pos = it }] as T?).also {
                curr = it
            } == null) return defaultValue
        if (key == curr) return this.key[pos] as T
        // There's always an unused entry.
        while (true) {
            if ((this.key[(pos + 1 and mask).also { pos = it }] as T?).also { curr = it } == null) return defaultValue
            if (key == curr) return this.key[pos] as T
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun putIfAbsent(k: T, v: T): T? {
        check(k === v)
        val pos = find(k)
        if (pos >= 0) return key[pos] as T
        check(k === v)
        insert(-pos - 1, k)
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun remove(key: T, value: T): Boolean {
        var curr: T?
        var pos: Int
        // The starting point.
        if ((this.key[(HashCommon.mix(key.hashCode()) and mask).also { pos = it }] as T?).also {
                curr = it
            } == null) return false
        if (key == curr && value == this.key[pos]) {
            removeEntry(pos)
            return true
        }
        while (true) {
            if ((this.key[(pos + 1 and mask).also { pos = it }] as T?).also { curr = it } == null) return false
            if (key == curr && value == this.key[pos]) {
                removeEntry(pos)
                return true
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun replace(k: T, oldValue: T, v: T): Boolean {
        val pos = find(k)
        if (pos < 0 || oldValue != key[pos]) return false
        key[pos] = v
        return true
    }

    /**
     * {@inheritDoc}
     */
    override fun replace(k: T, v: T): T? {
        val pos = find(k)
        if (pos < 0) return null
        val oldValue = key[pos] as T
        key[pos] = v
        return oldValue
    }

    /**
     * {@inheritDoc}
     */
    override fun computeIfAbsent(key: T, mappingFunction: Object2ObjectFunction<in T, out T>): T? {
        Objects.requireNonNull(mappingFunction)
        val pos = find(key)
        if (pos >= 0) return this.key[pos] as T
        if (!mappingFunction.containsKey(key)) return null
        val newValue = mappingFunction[key]
        check(key === newValue)
        insert(-pos - 1, key)
        return newValue
    }

    /**
     * {@inheritDoc}
     */
    override fun computeIfPresent(k: T, remappingFunction: BiFunction<in T, in T, out T?>): T? {
        Objects.requireNonNull(remappingFunction)
        val pos = find(k)
        if (pos < 0) return null
        if (key[pos] == null) return null
        val newValue = remappingFunction.apply(k, key[pos] as T)
        if (newValue == null) {
            removeEntry(pos)
            return null
        }
        return newValue.also { key[pos] = it }
    }

    /**
     * {@inheritDoc}
     */
    override fun compute(k: T, remappingFunction: BiFunction<in T, in T?, out T?>): T? {
        Objects.requireNonNull(remappingFunction)
        val pos = find(k)
        val newValue = remappingFunction.apply(k, if (pos >= 0) key[pos] as T? else null)
        if (newValue == null) {
            if (pos >= 0) {
                removeEntry(pos)
            }
            return null
        }
        if (pos < 0) {
            check(k === newValue)
            insert(-pos - 1, k)
            return newValue
        }
        return newValue.also { key[pos] = it }
    }

    /**
     * {@inheritDoc}
     */
    override fun merge(k: T, v: T, remappingFunction: BiFunction<in T, in T, out T?>): T? {
        Objects.requireNonNull(remappingFunction)
        Objects.requireNonNull(v)
        val pos = find(k)
        if (pos < 0 || key[pos] == null) {
            if (pos < 0) {
                check(k === v)
                insert(-pos - 1, k)
            } else key[pos] = v
            return v
        }
        val newValue = remappingFunction.apply(key[pos] as T, v)
        if (newValue == null) {
            removeEntry(pos)
            return null
        }
        return newValue.also { key[pos] = it }
    }

    /* Removes all elements from this map.
     *
     * <p>To increase object reuse, this method does not change the table size.
     * If you want to reduce the table size, you must use {@link #trim()}.
     *
     */
    override fun clear() {
        if (size0 == 0) return
        size0 = 0
        Arrays.fill(key, null)
    }

    override val size: Int
        get() {
            return size0
        }

    override fun isEmpty(): Boolean {
        return size0 == 0
    }

    /**
     * The entry class for a hash map does not record key and value, but rather the position in the hash
     * table of the corresponding entry. This is necessary so that calls to
     * [java.util.Map.Entry.setValue] are reflected in the map
     */
    inner class MapEntry : Object2ObjectMap.Entry<T, T>, MutableMap.MutableEntry<T, T>, Pair<T, T> {
        // The table index this entry refers to, or -1 if this entry has been deleted.
        var index = 0

        constructor(index: Int) {
            this.index = index
        }

        constructor() : super()

        override val key: T
            get() {
                return this@SelfHashMap.key[index] as T
            }

        override fun left(): T {
            return this@SelfHashMap.key[index] as T
        }

        override val value: T
            get() = this@SelfHashMap.key[index] as T

        override fun right(): T {
            return this@SelfHashMap.key[index] as T
        }

        override fun setValue(newValue: T): T {
            val oldValue = this@SelfHashMap.key[index] as T
            this@SelfHashMap.key[index] = newValue
            return oldValue
        }

        override fun right(v: T): Pair<T, T> {
            this@SelfHashMap.key[index] = v
            return this
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            val (key1, value1) = other as Map.Entry<T, T>
            return this@SelfHashMap.key[index] == key1 && this@SelfHashMap.key[index] == value1
        }

        override fun hashCode(): Int {
            return (if (this@SelfHashMap.key[index] == null) 0 else this@SelfHashMap.key[index]
                .hashCode()) xor if (this@SelfHashMap.key[index] == null) 0 else this@SelfHashMap.key[index]
                .hashCode()
        }

        override fun toString(): String {
            return this@SelfHashMap.key[index].toString() + "=>" + this@SelfHashMap.key[index]
        }
    }

    /**
     * An iterator over a hash map.
     */
    private abstract inner class MapIterator<ConsumerType> {
        /**
         * The index of the last entry returned, if positive or zero; initially, [.n]. If negative,
         * the last entry returned was that of the key of index `- pos - 1` from the [.wrapped]
         * list.
         */
        var pos = n

        /**
         * The index of the last entry that has been returned (more precisely, the value of [.pos] if
         * [.pos] is positive, or [Integer.MIN_VALUE] if [.pos] is negative). It is -1 if
         * either we did not return an entry yet, or the last returned entry has been removed.
         */
        var last = -1

        /**
         * A downward counter measuring how many entries must still be returned.
         */
        var c = size0

        /**
         * A boolean telling us whether we should return the entry with the null key.
         */
        var mustReturnNullKey = false

        /**
         * A lazily allocated list containing keys of entries that have wrapped around the table because of
         * removals.
         */
        var wrapped: ObjectArrayList<T?>? = null

        @Suppress("unused")
        abstract fun acceptOnIndex(action: ConsumerType, index: Int)
        operator fun hasNext(): Boolean {
            return c != 0
        }

        fun nextEntry(): Int {
            if (!hasNext()) throw NoSuchElementException()
            c--
            if (mustReturnNullKey) {
                mustReturnNullKey = false
                return n.also { last = it }
            }
            while (true) {
                if (--pos < 0) {
                    // We are just enumerating elements from the wrapped list.
                    last = Int.MIN_VALUE
                    val k = wrapped!![-pos - 1]
                    var p = HashCommon.mix(k.hashCode()) and mask
                    while (k != key[p]) p = p + 1 and mask
                    return p
                }
                if (key[pos] != null) return pos.also { last = it }
            }
        }

        open fun forEachRemaining(action: ConsumerType) {
            if (mustReturnNullKey) {
                mustReturnNullKey = false
                acceptOnIndex(action, n.also { last = it })
                c--
            }
            while (c != 0) {
                if (--pos < 0) {
                    // We are just enumerating elements from the wrapped list.
                    last = Int.MIN_VALUE
                    val k = wrapped!![-pos - 1]
                    var p = HashCommon.mix(k.hashCode()) and mask
                    while (k != key[p]) p = p + 1 and mask
                    acceptOnIndex(action, p)
                    c--
                } else if (key[pos] != null) {
                    acceptOnIndex(action, pos.also { last = it })
                    c--
                }
            }
        }

        /**
         * Shifts left entries with the specified hash code, starting at the specified position, and empties
         * the resulting free entry.
         *
         * @param pos a starting position.
         */
        @Suppress("NAME_SHADOWING")
        private fun shiftKeys(pos: Int) {
            // Shift entries with the same hash.
            var pos = pos
            var last: Int
            var slot: Int
            var curr: T?
            while (true) {
                pos = pos.also { last = it } + 1 and mask
                while (true) {
                    if ((key[pos] as T?).also { curr = it } == null) {
                        key[last] = null
                        key[last] = null
                        return
                    }
                    slot = HashCommon.mix(curr.hashCode()) and mask
                    if (if (last <= pos) last >= slot || slot > pos else last >= slot && slot > pos) break
                    pos = pos + 1 and mask
                }
                if (pos < last) { // Wrapped entry.
                    if (wrapped == null) wrapped = ObjectArrayList(2)
                    wrapped!!.add(key[pos] as T?)
                }
                key[last] = curr
            }
        }

        open fun remove() {
            check(last != -1)
            if (last == n) {
                key[n] = null
            } else if (pos >= 0) shiftKeys(last) else {
                // We're removing wrapped entries.
                this@SelfHashMap.remove(wrapped!!.set(-pos - 1, null))
                last = -1 // Note that we must not decrement size
                return
            }
            size0--
            last = -1 // You can no longer remove this entry.
        }

        open fun skip(n: Int): Int {
            var i = n
            while (i-- != 0 && hasNext()) nextEntry()
            return n - i - 1
        }
    }

    private inner class EntryIterator : MapIterator<Consumer<in Object2ObjectMap.Entry<T, T>>>(),
        ObjectIterator<Object2ObjectMap.Entry<T, T>> {
        private lateinit var entry: MapEntry

        override fun next(): MapEntry {
            return MapEntry(nextEntry()).also { entry = it }
        }

        override fun skip(n: Int): Int {
            return super<MapIterator>.skip(n)
        }

        override fun forEachRemaining(action: Consumer<in Object2ObjectMap.Entry<T, T>>) {
            return super<MapIterator>.forEachRemaining(action)
        }

        // forEachRemaining inherited from MapIterator superclass.
        override fun acceptOnIndex(action: Consumer<in Object2ObjectMap.Entry<T, T>>, index: Int) {
            action.accept(MapEntry(index).also { entry = it })
        }

        override fun remove() {
            super.remove()
            entry.index = -1 // You cannot use a deleted entry.
        }
    }

    private inner class FastEntryIterator : MapIterator<Consumer<in Object2ObjectMap.Entry<T, T>>>(),
        ObjectIterator<Object2ObjectMap.Entry<T, T>> {
        private val entry: MapEntry = MapEntry()
        override fun next(): MapEntry {
            entry.index = nextEntry()
            return entry
        }

        // forEachRemaining inherited from MapIterator superclass.
        override fun acceptOnIndex(action: Consumer<in Object2ObjectMap.Entry<T, T>>, index: Int) {
            entry.index = index
            action.accept(entry)
        }

        override fun forEachRemaining(action: Consumer<in Object2ObjectMap.Entry<T, T>>) {
            return super<MapIterator>.forEachRemaining(action)
        }

        override fun skip(n: Int): Int {
            return super<MapIterator>.skip(n)
        }
    }

    private inner class MapEntrySet : AbstractObjectSet<Object2ObjectMap.Entry<T, T>>(),
        Object2ObjectMap.FastEntrySet<T, T> {
        override fun iterator(): ObjectIterator<Object2ObjectMap.Entry<T, T>> {
            return EntryIterator()
        }

        override fun fastIterator(): ObjectIterator<Object2ObjectMap.Entry<T, T>> {
            return FastEntryIterator()
        }

        //
        override operator fun contains(element: Object2ObjectMap.Entry<T, T>?): Boolean {
            if (element !is Map.Entry<*, *>) return false
            val k: T = element.key as T
            val v = element.value as T
            var curr: T?
            var pos: Int
            // The starting point.
            if ((key[(HashCommon.mix(k.hashCode()) and mask).also { pos = it }] as T?).also {
                    curr = it
                } == null) return false
            if (k == curr) return key[pos] == v
            // There's always an unused entry.
            while (true) {
                if ((key[(pos + 1 and mask).also { pos = it }] as T?).also { curr = it } == null) return false
                if (k == curr) return key[pos] == v
            }
        }

        override fun remove(element: Object2ObjectMap.Entry<T, T>?): Boolean {
            if (element !is Map.Entry<*, *>) return false
            val k: T = element.key as T
            val v = element.value as T
            var curr: T?
            var pos: Int
            // The starting point.
            if ((key[(HashCommon.mix(k.hashCode()) and mask).also { pos = it }] as T?).also {
                    curr = it
                } == null) return false
            if (curr == k) {
                if (key[pos] == v) {
                    removeEntry(pos)
                    return true
                }
                return false
            }
            while (true) {
                if ((key[(pos + 1 and mask).also { pos = it }] as T?).also { curr = it } == null) return false
                if (curr == k) {
                    if (key[pos] == v) {
                        removeEntry(pos)
                        return true
                    }
                }
            }
        }

        override val size: Int
            get() {
                return this@SelfHashMap.size0
            }

        override fun clear() {
            this@SelfHashMap.clear()
        }

        /**
         * {@inheritDoc}
         */
        override fun forEach(consumer: Consumer<in Object2ObjectMap.Entry<T, T>>) {
            var pos = n
            while (pos-- != 0) {
                if (key[pos] != null) consumer.accept(Entry(key[pos] as T))
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun fastForEach(consumer: Consumer<in Object2ObjectMap.Entry<T, T>>) {
            val entry = Entry<T>()
            var pos = n
            while (pos-- != 0) {
                if (key[pos] != null) {
                    entry.kv = key[pos] as T
                    consumer.accept(entry)
                }
            }
        }

        inner class Entry<T : Any> : Object2ObjectMap.Entry<T, T> {
            lateinit var kv: T

            constructor() : super()
            constructor(key: T) {
                kv = key
            }

            override val key: T
                get() {
                    return kv
                }

            override val value: T
                get() = kv

            override fun setValue(newValue: T): T {
                throw UnsupportedOperationException()
            }

            override fun equals(other: Any?): Boolean {
                if (other !is Map.Entry<*, *>) return false
                if (other is Object2ObjectMap.Entry<*, *>) {
                    val (key1, value1) = other as Object2ObjectMap.Entry<T, T>
                    return kv == key1 && kv == value1
                }
                val (key1, value1) = other
                val key = key1!!
                val value = value1!!
                return kv == key && kv == value
            }

            override fun hashCode(): Int {
                return (kv.hashCode()) xor kv.hashCode()
            }

            override fun toString(): String {
                return "$kv->$kv"
            }
        }
    }

    override fun object2ObjectEntrySet(): Object2ObjectMap.FastEntrySet<T, T> {
        if (entries0 == null) entries0 = MapEntrySet()
        return entries0!!
    }

    /**
     * An iterator on keys.
     *
     *
     *
     * We simply override the
     * [java.util.ListIterator.next]/[java.util.ListIterator.previous] methods (and
     * possibly their type-specific counterparts) so that they return keys instead of entries.
     */
    private inner class KeyIterator : MapIterator<Consumer<in T>>(), ObjectIterator<T> {
        // forEachRemaining inherited from MapIterator superclass.
        // Despite the superclass declared with generics, the way Java inherits and generates bridge methods
        // avoids the boxing/unboxing
        override fun acceptOnIndex(action: Consumer<in T>, index: Int) {
            action.accept(key[index] as T)
        }

        override fun next(): T {
            return key[nextEntry()] as T
        }

        override fun forEachRemaining(action: Consumer<in T>) {
            return super<MapIterator>.forEachRemaining(action)
        }

        override fun skip(n: Int): Int {
            return super<MapIterator>.skip(n)
        }
    }


    private inner class KeySet : AbstractObjectSet<T>() {
        override fun iterator(): ObjectIterator<T> {
            return KeyIterator()
        }

        /**
         * {@inheritDoc}
         */
        override fun forEach(consumer: Consumer<in T>) {
            var pos = n
            while (pos-- != 0) {
                val k = key[pos] as T?
                if (k != null) consumer.accept(k)
            }
        }

        override val size: Int
            get() {
                return this@SelfHashMap.size0
            }

        override operator fun contains(element: T): Boolean {
            return containsKey(element)
        }

        override fun remove(element: T): Boolean {
            val oldSize = size
            this@SelfHashMap.remove(element)
            return size != oldSize
        }

        override fun clear() {
            this@SelfHashMap.clear()
        }
    }


    override val keys: ObjectSet<T>
        get() {
            if (keys0 == null) keys0 = KeySet()
            return keys0!!
        }

    /**
     * An iterator on values.
     *
     *
     *
     * We simply override the
     * [java.util.ListIterator.next]/[java.util.ListIterator.previous] methods (and
     * possibly their type-specific counterparts) so that they return values instead of entries.
     */
    private inner class ValueIterator : MapIterator<Consumer<in T>>(), ObjectIterator<T> {
        // forEachRemaining inherited from MapIterator superclass.
        // Despite the superclass declared with generics, the way Java inherits and generates bridge methods
        // avoids the boxing/unboxing
        override fun acceptOnIndex(action: Consumer<in T>, index: Int) {
            action.accept(key[index] as T)
        }

        override fun next(): T {
            return key[nextEntry()] as T
        }

        override fun forEachRemaining(action: Consumer<in T>) {
            return super<MapIterator>.forEachRemaining(action)
        }

        override fun skip(n: Int): Int {
            return super<MapIterator>.skip(n)
        }
    }

    override val values: ObjectCollection<T>
        get() {
            return keys
        }
    /**
     * Rehashes this map if the table is too large.
     *
     *
     *
     * Let <var>N</var> be the smallest table size that can hold `max(n,[.size])`
     * entries, still satisfying the load factor. If the current table size is smaller than or equal to
     * <var>N</var>, this method does nothing. Otherwise, it rehashes this map in a table of size
     * <var>N</var>.
     *
     *
     *
     * This method is useful when reusing maps. [Clearing a map][.clear] leaves the table
     * size untouched. If you are reusing a map many times, you can call this method with a typical size
     * to avoid keeping around a very large table just because of a few large transient maps.
     *
     * @param n the threshold for the trimming.
     * @return true if there was enough memory to trim the map.
     * @see .trim
     */
    /**
     * Rehashes the map, making the table as small as possible.
     *
     *
     *
     * This method rehashes the table to the smallest size satisfying the load factor. It can be used
     * when the set will not be changed anymore, so to optimize access speed and size.
     *
     *
     *
     * If the table size is already the minimum possible, this method does nothing.
     *
     * @return true if there was enough memory to trim the map.
     * @see .trim
     */
    @JvmOverloads
    fun trim(n: Int = size0): Boolean {
        val l = HashCommon.nextPowerOfTwo(ceil((n / f).toDouble()).toInt())
        if (l >= this.n || size0 > HashCommon.maxFill(l, f)) return true
        try {
            rehash(l)
        } catch (cantDoIt: OutOfMemoryError) {
            return false
        }
        return true
    }

    /**
     * Rehashes the map.
     *
     *
     *
     * This method implements the basic rehashing strategy, and may be overridden by subclasses
     * implementing different rehashing strategies (e.g., disk-based rehashing). However, you should not
     * override this method unless you understand the workings of this class.
     *
     * @param newN the new size
     */
    @Suppress("ControlFlowWithEmptyBody")
    private fun rehash(newN: Int) {
        val mask = newN - 1 // Note that this is used by the hashing macro
        val newKey = arrayOfNulls<Any>(newN + 1)
        var i = n
        var pos: Int
        var j = size0
        while (j-- != 0) {
            while (key[--i] == null);
            if (newKey[(HashCommon.mix(key[i].hashCode()) and mask).also {
                    pos = it
                }] != null) while (newKey[(pos + 1 and mask).also { pos = it }] != null);
            newKey[pos] = key[i] as T?
        }
        n = newN
        this.mask = mask
        maxFill = HashCommon.maxFill(n, f)
        key = newKey
    }

    /**
     * Returns a deep copy of this map.
     *
     *
     *
     * This method performs a deep copy of this hash map; the data stored in the map, however, is not
     * cloned. Note that this makes a difference only for object keys.
     *
     * @return a deep copy of this map.
     */
    public override fun clone(): SelfHashMap<T> {
        val c = try {
            super.clone() as SelfHashMap<T>
        } catch (cantHappen: CloneNotSupportedException) {
            throw InternalError()
        }
        c.keys0 = null
        c.entries0 = null
        c.key = key.clone()
        return c
    }

    /**
     * Returns a hash code for this map.
     *
     *
     * This method overrides the generic method provided by the superclass. Since `equals()` is
     * not overriden, it is important that the value returned by this method is the same value as the
     * one returned by the overriden method.
     *
     * @return a hash code for this map.
     */
    @Suppress("SpellCheckingInspection")
    override fun hashCode(): Int {
        var h = 0
        var j = size0
        var i = 0
        var t = 0
        while (j-- != 0) {
            while (key[i] == null) i++
            if (this !== key[i]) t = key[i].hashCode()
            h += t
            i++
        }
        return h
    }
}