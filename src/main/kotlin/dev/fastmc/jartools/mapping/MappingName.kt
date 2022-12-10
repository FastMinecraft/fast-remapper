package dev.fastmc.jartools.mapping

sealed class MappingName(val type: MappingType) {
    abstract val identifier: String

    final override fun toString(): String {
        return identifier
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
        override val identifier: String
            get() = "mcp.$channel.$version"
    }
}