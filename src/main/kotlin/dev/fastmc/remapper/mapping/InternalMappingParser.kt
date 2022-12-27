package dev.fastmc.remapper.mapping

import dev.fastmc.remapper.util.appendHexLong
import dev.fastmc.remapper.util.toHexLong
import java.io.Reader

@Suppress("unused", "MemberVisibilityCanBePrivate")
object InternalMappingParser {
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
                lastClassEntry = MappingEntry.MutableClass(split[0], split[1], split[2].toHexLong())
                mapping.add(lastClassEntry!!)
            } else {
                if (split.size == 5) {
                    lastClassEntry!!.methodMapping.add(
                        MappingEntry.Method(
                            split[1],
                            split[2],
                            split[3],
                            split[4].toHexLong()
                        )
                    )
                } else {
                    lastClassEntry!!.fieldMapping.add(
                        MappingEntry.Field(
                            split[1],
                            split[2],
                            split[3].toHexLong()
                        )
                    )
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return mapping as MutableMappingEntryMap<MappingEntry.Class>
    }

    fun write(appendable: Appendable, classMapping: ClassMapping) {
        val sortedArray = classMapping.sortedArray()
        for (entry in sortedArray) {
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
        appendable.appendHexLong(entry.hashCodeLong())
        appendable.append('\n')

        if (!entry.fieldMapping.isEmpty()) {
            for (field in entry.fieldMapping.sortedArray()) {
                appendable.append('\t')
                appendable.append(field.nameFrom)
                appendable.append('\t')
                appendable.append(field.nameTo)
                appendable.append('\t')
                appendable.appendHexLong(field.hashCodeLong())
                appendable.append('\n')
            }
        }

        if (!entry.methodMapping.isEmpty()) {
            for (method in entry.methodMapping.sortedArray()) {
                appendable.append('\t')
                appendable.append(method.nameFrom)
                appendable.append('\t')
                appendable.append(method.desc)
                appendable.append('\t')
                appendable.append(method.nameTo)
                appendable.append('\t')
                appendable.appendHexLong(method.hashCodeLong())
                appendable.append('\n')
            }
        }

        return appendable
    }

    private inline fun <reified T : MappingEntry> MappingEntryMap<T>.sortedArray(): Array<T> {
        return this.backingMap.toTArray<T>().apply { sort() }
    }
}