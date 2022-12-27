package dev.fastmc.remapper.mapping

enum class MappingType(val identifier: String) {
    OBFUSCATED("obfuscated"),
    INTERMEDIARY("intermediary"),
    YARN("yarn"),
    MOJANG("mojang"),
    SEARGE("searge"),
    MCP("mcp");
}