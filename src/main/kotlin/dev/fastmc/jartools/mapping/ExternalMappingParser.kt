package dev.fastmc.jartools.mapping

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
                    lastClassEntry = result.getOrCreate(split[0], split[1])
                } else {
                    when (it[1]) {
                        'f' -> {
                            val split = it.subSequence(3, it.length).split('\t')
                            lastClassEntry!!.fieldMapping.add(MappingEntry.Field(split[1], split[2]))
                        }
                        'm' -> {
                            val split = it.subSequence(3, it.length).split('\t')
                            lastClassEntry!!.methodMapping.add(MappingEntry.Method(split[1], split[0], split[2]))
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
    TSRG2 {
        override fun parse(lines: List<String>): ClassMapping {
            val result: MutableClassMapping = MutableClassMapping()
            var lastClassEntry: MappingEntry.MutableClass? = null
            var skippedHeader = !lines[0].startsWith("tsrg2")

            lines.forEach {
                if (!skippedHeader) {
                    skippedHeader = true
                    return@forEach
                }
                if (it.isEmpty()) return@forEach

                if (it[0] != '\t') {
                    val split = it.split(' ')
                    lastClassEntry = result.getOrCreate(split[0], split[1])
                } else if (it[1] != '\t')  {
                    val split = it.subSequence(1, it.length).split(' ')
                    if (split.size == 3) {
                        lastClassEntry!!.methodMapping.add(MappingEntry.Method(split[0], split[1], split[2]))
                    } else {
                        lastClassEntry!!.fieldMapping.add(MappingEntry.Field(split[0], split[1]))
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