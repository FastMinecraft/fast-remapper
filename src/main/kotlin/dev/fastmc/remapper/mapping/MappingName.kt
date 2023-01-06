package dev.fastmc.remapper.mapping

import java.io.Serializable

sealed class MappingName(@Transient val type: MappingType) : Comparable<MappingName>, Serializable {
    abstract val identifier: String

    final override fun toString(): String {
        return identifier
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MappingName) return false

        if (type != other.type) return false
        if (identifier != other.identifier) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + identifier.hashCode()
        return result
    }

    override fun compareTo(other: MappingName): Int {
        return identifier.compareTo(other.identifier)
    }

    object Obfuscated : MappingName(MappingType.OBFUSCATED) {
        override val identifier: String
            get() = "obfuscated"
    }

    object YarnIntermediary : MappingName(MappingType.INTERMEDIARY) {
        override val identifier: String
            get() = "intermediary"
    }

    class Yarn(val buildNumber: Int) : MappingName(MappingType.YARN) {
        override val identifier: String
            get() = "yarn.$buildNumber"
    }

    object Mojang : MappingName(MappingType.MOJANG) {
        override val identifier: String
            get() = "mojang"
    }

    object Searge : MappingName(MappingType.SEARGE) {
        override val identifier: String
            get() = "searge"
    }

    class Mcp(val channel: String, val version: Int) : MappingName(MappingType.MCP) {
        constructor(channel: String, version: String) : this(channel, version.toInt())

        override val identifier: String
            get() = "mcp.$channel.$version"
    }
}