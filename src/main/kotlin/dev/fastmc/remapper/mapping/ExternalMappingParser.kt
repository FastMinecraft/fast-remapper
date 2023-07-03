package dev.fastmc.remapper.mapping

import java.io.Reader

enum class ExternalMappingParser {
    TINY {
        override fun parse(lines: List<String>): ClassMapping {
            val result: MutableClassMapping = MutableClassMapping()
            var lastClassEntry: MappingEntry.MutableClass? = null
            var skippedHeader = false

            lines.forEach {
                if (!skippedHeader) {
                    skippedHeader = true
                    return@forEach
                }

                if (it.isEmpty()) return@forEach

                if (it[0] == 'c') {
                    val split = it.subSequence(2, it.length).split('\t')
                    lastClassEntry = result.getOrCreate(
                        split[0],
                        split[1].ifEmpty { split[0] }
                    )
                } else {
                    when (it[1]) {
                        'f' -> {
                            val split = it.subSequence(3, it.length).split('\t')
                            val prevSize = lastClassEntry!!.fieldMapping.size
                            lastClassEntry!!.fieldMapping.add(
                                MappingEntry.Field(
                                    split[1],
                                    split[2].ifEmpty { split[1] })
                            )
                            assert(lastClassEntry!!.fieldMapping.size == prevSize + 1)
                        }
                        'm' -> {
                            val split = it.subSequence(3, it.length).split('\t')
                            val prevSize = lastClassEntry!!.methodMapping.size
                            lastClassEntry!!.methodMapping.add(
                                MappingEntry.Method(
                                    split[0],
                                    split[1],
                                    split[2].ifEmpty { split[0] }
                                )
                            )
                            assert(lastClassEntry!!.methodMapping.size == prevSize + 1)
                        }
                        else -> {
                            // Ignored
                        }
                    }
                }
            }

            return result.asImmutable()
        }
    },
    TSRG {
        override fun parse(lines: List<String>): ClassMapping {
            val result: MutableClassMapping = MutableClassMapping()
            var lastClassEntry: MappingEntry.MutableClass? = null
            var skippedHeader = !lines[0].startsWith("tsrg2")
            val methodFieldCount = if (lines[0].split(' ').size >=4) 4 else 3

            lines.forEach {
                if (!skippedHeader) {
                    skippedHeader = true
                    return@forEach
                }
                if (it.isEmpty()) return@forEach

                if (it[0] != '\t') {
                    val split = it.split(' ')
                    val prevSize = result.size
                    lastClassEntry = result.getOrCreate(
                        split[0],
                        split[1].ifEmpty { split[0] }
                    )
                    assert(result.size == prevSize + 1)
                } else if (it[1] != '\t') {
                    val split = it.subSequence(1, it.length).split(' ')
                    if (split.size >= methodFieldCount) {
                        val prevSize = lastClassEntry!!.methodMapping.size
                        lastClassEntry!!.methodMapping.add(
                            MappingEntry.Method(
                                split[0],
                                split[1],
                                split[2].ifEmpty { split[0] }
                            )
                        )
                        assert(lastClassEntry!!.methodMapping.size == prevSize + 1)
                    } else {
                        val prevSize = lastClassEntry!!.fieldMapping.size
                        lastClassEntry!!.fieldMapping.add(
                            MappingEntry.Field(
                                split[0],
                                split[1].ifEmpty { split[0] }
                            )
                        )
                        assert(lastClassEntry!!.fieldMapping.size == prevSize + 1)
                    }
                }
            }

            return result.asImmutable()
        }
    };

    fun parse(string: String): ClassMapping {
        return parse(string.reader())
    }

    fun parse(input: Reader): ClassMapping {
        return parse(input.readLines())
    }

    abstract fun parse(lines: List<String>): ClassMapping
}