package dev.fastmc.jartools.remap

import java.io.Reader

@Suppress("NOTHING_TO_INLINE", "unused", "MemberVisibilityCanBePrivate")
object MappingSerialization {
    fun read(string: String): ClassMapping {
        return read(string.reader())
    }

    fun read(input: Reader): ClassMapping {
        return input.useLines {
            read(it)
        }
    }

    fun read(lines: Sequence<String>): ClassMapping {
        val mapping = MutableClassMapping()
        var lastClassEntry: MappingEntry.MutableClass? = null
        lines.forEach {
            if (it.isEmpty()) return@forEach
            val split = it.split('\t')
            if (it[0] != '\t') {
                lastClassEntry = MappingEntry.MutableClass(split[0], split[1], split[2].toHexInt())
                mapping.add(lastClassEntry!!)
            } else {
                if (split.size == 5) {
                    lastClassEntry!!.methodMapping.add(
                        MappingEntry.Method(
                            split[1],
                            split[2],
                            split[3],
                            split[4].toHexInt()
                        )
                    )
                } else {
                    lastClassEntry!!.fieldMapping.add(
                        MappingEntry.Field(
                            split[1],
                            split[2],
                            split[3].toHexInt()
                        )
                    )
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return mapping as MutableMappingEntryMap<MappingEntry.Class>
    }

    fun write(appendable: Appendable, classMapping: ClassMapping) {
        for (entry in classMapping.entries.sortedArray()) {
            write(appendable, entry)
        }
    }

    private fun write(
        appendable: Appendable,
        entry: MappingEntry.Class,
    ): Appendable {
        appendable.append(entry.nameFrom)
        appendable.append('\t')
        appendable.append(entry.nameTo)
        appendable.append('\t')
        appendable.appendHex(entry.hashCode())
        appendable.append('\n')

        if (!entry.fieldMapping.isEmpty()) {
            for (field in entry.fieldMapping.entries.sortedArray()) {
                appendable.append('\t')
                appendable.append(field.nameFrom)
                appendable.append('\t')
                appendable.append(field.nameTo)
                appendable.append('\t')
                appendable.appendHex(field.hashCode())
                appendable.append('\n')
            }
        }

        if (!entry.methodMapping.isEmpty()) {
            for (method in entry.methodMapping.entries.sortedArray()) {
                appendable.append('\t')
                appendable.append(method.nameFrom)
                appendable.append('\t')
                appendable.append(method.desc)
                appendable.append('\t')
                appendable.append(method.nameTo)
                appendable.append('\t')
                appendable.appendHex(method.hashCode())
                appendable.append('\n')
            }
        }

        return appendable
    }

    private inline fun <reified T> Collection<T>.sortedArray(): Array<T> {
        return this.toTypedArray().apply { sort() }
    }

    private val hexChars = "0123456789ABCDEF".toCharArray()

    private fun Appendable.appendHex(value: Int): Appendable {
        append(hexChars[(value ushr 28) and 0xF])
        append(hexChars[(value ushr 24) and 0xF])
        append(hexChars[(value ushr 20) and 0xF])
        append(hexChars[(value ushr 16) and 0xF])
        append(hexChars[(value ushr 12) and 0xF])
        append(hexChars[(value ushr 8) and 0xF])
        append(hexChars[(value ushr 4) and 0xF])
        append(hexChars[value and 0xF])
        return this
    }

    private val hexValues = IntArray(128).apply {
        for (i in 0..9) {
            this[i + '0'.code] = i
        }
        for (i in 0..5) {
            this[i + 'A'.code] = i + 10
            this[i + 'a'.code] = i + 10
        }
    }

    private inline fun String.toHexInt(): Int {
        var value = 0
        value = value or (hexValues[this[0].code and 0x7F] shl 28)
        value = value or (hexValues[this[1].code and 0x7F] shl 24)
        value = value or (hexValues[this[2].code and 0x7F] shl 20)
        value = value or (hexValues[this[3].code and 0x7F] shl 16)
        value = value or (hexValues[this[4].code and 0x7F] shl 12)
        value = value or (hexValues[this[5].code and 0x7F] shl 8)
        value = value or (hexValues[this[6].code and 0x7F] shl 4)
        value = value or hexValues[this[7].code and 0x7F]
        return value
    }
}