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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Yarn) return false
            if (!super.equals(other)) return false

            if (buildNumber != other.buildNumber) return false
            return identifier == other.identifier
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + buildNumber.hashCode()
            result = 31 * result + identifier.hashCode()
            return result
        }

        override fun compareTo(other: MappingName): Int {
            if (other is Yarn) {
                return buildNumber.compareTo(other.buildNumber)
            }
            return super.compareTo(other)
        }
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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Mcp) return false
            if (!super.equals(other)) return false

            if (channel != other.channel) return false
            if (version != other.version) return false
            return identifier == other.identifier
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + channel.hashCode()
            result = 31 * result + version.hashCode()
            result = 31 * result + identifier.hashCode()
            return result
        }

        override fun compareTo(other: MappingName): Int {
            if (other is Mcp) {
                var v = channel.compareTo(other.channel)
                if (v == 0) {
                    v = version.compareTo(other.version)
                }
                return v
            }
            return super.compareTo(other)
        }
    }
}