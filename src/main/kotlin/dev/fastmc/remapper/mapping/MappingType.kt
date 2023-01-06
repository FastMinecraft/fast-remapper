package dev.fastmc.remapper.mapping

import java.io.Serializable

enum class MappingType(val identifier: String) : Serializable {
    OBFUSCATED("obfuscated"),
    INTERMEDIARY("intermediary"),
    YARN("yarn"),
    MOJANG("mojang"),
    SEARGE("searge"),
    MCP("mcp");
}