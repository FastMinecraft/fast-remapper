package dev.fastmc.remapper

import dev.fastmc.remapper.util.McVersion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class McVersionTest {
    @Test
    fun testMcVersions() {
        getMcVersions().forEach {
            assertEquals(McVersion(it).toString(), it, "McVersion($it) != $it")
        }
    }
}