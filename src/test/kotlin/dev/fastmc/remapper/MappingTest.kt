package dev.fastmc.remapper

import com.google.gson.JsonParser
import dev.fastmc.remapper.mapping.MappingName
import dev.fastmc.remapper.mapping.MappingProvider
import dev.fastmc.remapper.mapping.reversed
import dev.fastmc.remapper.util.GlobalMavenCache
import dev.fastmc.remapper.util.McVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File

class MappingTest {
    val srgVersions: List<McVersion>
    val mcpVersions: Map<McVersion, Map<String, List<MappingName.Mcp>>>

    init {
        val srgVersions = mutableListOf<McVersion>()
        val mcpVersions = mutableMapOf<McVersion, Map<String, List<MappingName.Mcp>>>()
        this.srgVersions = srgVersions
        this.mcpVersions = mcpVersions

        runBlocking {
            val gradleHome = System.getenv("GRADLE_USER_HOME") ?: (System.getProperty("user.home") + "/.gradle")
            Shared.mavenCacheDir = File(gradleHome, "caches/fast-remapper/maven").also {
                it.mkdirs()
            }
            Shared.globalCacheDir = File(System.getProperty("user.dir"), "build/tmp/fast-remapper-test").also {
                it.mkdirs()
            }
            MappingProvider.cacheDir.listFiles()?.forEach { it.deleteRecursively() }

            getMavenVersion("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/maven-metadata.xml")
                .map {
                    it.substringBefore('-')
                }
                .distinct()
                .mapTo(srgVersions) { McVersion(it) }

            val mcpVersionUrl = "https://files.minecraftforge.net/maven/de/oceanlabs/mcp/versions.json"
            val mcpVersionFile = GlobalMavenCache.getMaven(mcpVersionUrl).await()!!
            val mcpVersion = JsonParser.parseString(mcpVersionFile.readText()).asJsonObject.asMap()

            mcpVersion.forEach { (mcVersion, obj) ->
                val list = obj.asJsonObject.asMap().entries.associate { (channel, versions) ->
                    channel to versions.asJsonArray.map {
                        MappingName.Mcp(channel, it.asInt)
                    }
                }
                mcpVersions[McVersion(mcVersion)] = list
            }
        }
    }

    @Test
    fun testObf2Srg() {
        runBlocking {
            srgVersions.forEach {
                launch(Dispatchers.Default) {
                    MappingProvider.Obf2Searge.provide(it)
                }
            }
        }
    }

    @Test
    fun testSrg2Obf() {
        runBlocking {
            srgVersions.forEach {
                launch(Dispatchers.Default) {
                    MappingProvider.getOrCompute(it, MappingName.Searge, MappingName.Obfuscated) {
                        MappingProvider.Obf2Searge.provide(it).reversed()
                    }
                }
            }
        }
    }

    @Test
    fun testSrg2Mcp() {
        runBlocking {
            srgVersions.forEach { mcVersion ->
                if (mcVersion < McVersion("1.12.2")) return@forEach

                var testMcVersion = mcVersion
                var map = mcpVersions[testMcVersion]
                if (map == null) {
                    testMcVersion = McVersion(testMcVersion.one, testMcVersion.two)
                    map = mcpVersions[testMcVersion]
                }
                map ?: return@forEach

                map.values.forEach { versions ->
                    versions.take(5).forEach {
                        launch(Dispatchers.Default) {
                            MappingProvider.Searge2Mcp.provide(mcVersion, it)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testMcp2Srg() {
        runBlocking {
            srgVersions.forEach { mcVersion ->
                val map = getMcpVersionMap(mcVersion) ?: return@forEach

                map.values.forEach { versions ->
                    versions.take(5).forEach {
                        launch(Dispatchers.Default) {
                            MappingProvider.getOrCompute(mcVersion, it, MappingName.Searge) {
                                MappingProvider.Searge2Mcp.provide(mcVersion, it).reversed()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testObf2Mcp() {
        runBlocking {
            srgVersions.forEach { mcVersion ->
                val map = getMcpVersionMap(mcVersion) ?: return@forEach

                map.values.forEach { versions ->
                    versions.take(5).forEach {
                        launch(Dispatchers.Default) {
                            MappingProvider.getObf2Mcp(mcVersion, it)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun tesMcp2Obf() {
        runBlocking {
            srgVersions.forEach { mcVersion ->
                val map = getMcpVersionMap(mcVersion) ?: return@forEach

                map.values.forEach { versions ->
                    versions.take(5).forEach {
                        launch(Dispatchers.Default) {
                            MappingProvider.getMcp2Obf(mcVersion, it)
                        }
                    }
                }
            }
        }
    }

    private fun getMcpVersionMap(mcVersion: McVersion): Map<String, List<MappingName.Mcp>>? {
        if (mcVersion < McVersion("1.12.2")) return null
        var testMcVersion = mcVersion
        var map = mcpVersions[testMcVersion]
        if (map == null) {
            testMcVersion = McVersion(testMcVersion.one, testMcVersion.two)
            map = mcpVersions[testMcVersion]
        }
        return map
    }
}