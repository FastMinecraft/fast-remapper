package dev.fastmc.remapper

import com.google.gson.JsonParser
import dev.fastmc.remapper.mapping.MappingName
import dev.fastmc.remapper.mapping.MappingProvider
import dev.fastmc.remapper.util.GlobalMavenCache
import dev.fastmc.remapper.util.McVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import java.util.*

class MappingTest {
    val mcReleaseVersion = getMcVersions().toSet()
    val srgVersions: Set<McVersion>
    val mcpVersions: Map<McVersion, Map<String, Set<MappingName.Mcp>>>
    val intermediaryVersions: Set<McVersion>
    val yarnVersions: Map<McVersion, Set<MappingName.Yarn>>

    init {
        val srgVersions = TreeSet<McVersion>()
        val mcpVersions = TreeMap<McVersion, Map<String, Set<MappingName.Mcp>>>()
        val intermediaryVersions = TreeSet<McVersion>()
        val yarnVersions = TreeMap<McVersion, MutableSet<MappingName.Yarn>>()
        this.srgVersions = srgVersions
        this.mcpVersions = mcpVersions
        this.intermediaryVersions = intermediaryVersions
        this.yarnVersions = yarnVersions

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
                .filter { it in mcReleaseVersion }
                .distinct()
                .mapTo(srgVersions) { McVersion(it) }

            val mcpVersionUrl = "https://files.minecraftforge.net/maven/de/oceanlabs/mcp/versions.json"
            val mcpVersionFile = GlobalMavenCache.getMaven(mcpVersionUrl).await()!!
            val mcpVersion = JsonParser.parseString(mcpVersionFile.readText()).asJsonObject.asMap()

            mcpVersion.forEach { (mcVersion, obj) ->
                val map = obj.asJsonObject.asMap().entries.associate { (channel, versions) ->
                    channel to versions.asJsonArray.map {
                        MappingName.Mcp(channel, it.asInt)
                    }.toSortedSet()
                }
                mcpVersions[McVersion(mcVersion)] = map
            }

            intermediaryVersions(intermediaryVersions, "https://meta.fabricmc.net/v2/versions/intermediary")
//            intermediaryVersions(intermediaryVersions, "https://meta.legacyfabric.net/v2/versions/intermediary")

            yarnVersions(yarnVersions, "https://meta.fabricmc.net/v2/versions/yarn")
//            yarnVersions(yarnVersions, "https://meta.legacyfabric.net/v2/versions/yarn")
        }
    }

    private fun intermediaryVersions(set: MutableSet<McVersion>, url: String) {
        JsonParser.parseString(URL(url).readText()).asJsonArray.asSequence()
            .map { it.asJsonObject.getAsJsonPrimitive("version").asString }
            .filter { it in mcReleaseVersion }
            .mapTo(set) { McVersion(it) }
    }

    private fun yarnVersions(map: MutableMap<McVersion, MutableSet<MappingName.Yarn>>, url: String) {
        JsonParser.parseString(URL(url).readText()).asJsonArray.asSequence()
            .map { it.asJsonObject }
            .filter { it.getAsJsonPrimitive("separator").asString == "+build." }
            .map { it.getAsJsonPrimitive("gameVersion").asString to it.getAsJsonPrimitive("build").asInt }
            .filter { it.first in mcReleaseVersion }
            .map { McVersion(it.first) to MappingName.Yarn(it.second) }
            .forEach {
                map.getOrPut(it.first) { TreeSet() }.add(it.second)
            }
    }

    @Test
    fun testObfSrg() {
        runBlocking {
            srgVersions.forEach {
                launch(Dispatchers.Default) {
                    MappingProvider.getObf2Srg(it)
                    MappingProvider.getSrg2Obf(it)
                }
            }
        }
    }

    @Test
    fun testSrgMcp() {
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
                            MappingProvider.getSrg2Mcp(mcVersion, it)
                            MappingProvider.getMcp2Srg(mcVersion, it)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testObfMcp() {
        runBlocking {
            srgVersions.forEach { mcVersion ->
                val map = getMcpVersionMap(mcVersion) ?: return@forEach

                map.values.forEach { versions ->
                    versions.take(5).forEach {
                        launch(Dispatchers.Default) {
                            MappingProvider.getObf2Mcp(mcVersion, it)
                            MappingProvider.getMcp2Obf(mcVersion, it)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testObfIntermediary() {
        runBlocking {
            intermediaryVersions.forEach { mcVersion ->
                launch(Dispatchers.Default) {
                    MappingProvider.getObf2Intermediary(mcVersion)
                    MappingProvider.getIntermediary2Obf(mcVersion)
                }
            }
        }
    }

    @Test
    fun testIntermediaryYarn() {
        runBlocking {
            yarnVersions.forEach { (mcVersion, versions) ->
                launch(Dispatchers.Default) {
                    MappingProvider.getIntermediary2Yarn(mcVersion, versions.last())
                    MappingProvider.getYarn2Intermediary(mcVersion, versions.last())
                }
            }
        }
    }

    @Test
    fun testObfYarn() {
        runBlocking {
            yarnVersions.forEach { (mcVersion, versions) ->
                launch(Dispatchers.Default) {
                    MappingProvider.getObf2Yarn(mcVersion, versions.last())
                    MappingProvider.getYarn2Obf(mcVersion, versions.last())
                }
            }
        }
    }

    @Test
    fun testSrgIntermediary() {
        runBlocking {
            intermediaryVersions.forEach { mcVersion ->
                getMcpVersionMap(mcVersion) ?: return@forEach
                launch(Dispatchers.Default) {
                    MappingProvider.getSrg2Intermediary(mcVersion)
                    MappingProvider.getIntermediary2Srg(mcVersion)
                }
            }
        }
    }

    @Test
    fun testMcpIntermediary() {
        runBlocking {
            intermediaryVersions.forEach { mcVersion ->
                val map = getMcpVersionMap(mcVersion) ?: return@forEach

                map.values.forEach { versions ->
                    versions.take(5).forEach {
                        launch(Dispatchers.Default) {
                            MappingProvider.getMcp2Intermediary(mcVersion, it)
                            MappingProvider.getIntermediary2Mcp(mcVersion, it)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testSrgYarn() {
        runBlocking {
            yarnVersions.forEach { (mcVersion, versions) ->
                if (mcVersion !in srgVersions) return@forEach
                launch(Dispatchers.Default) {
                    MappingProvider.getSrg2Yarn(mcVersion, versions.last())
                    MappingProvider.getYarn2Srg(mcVersion, versions.last())
                }
            }
        }
    }

    @Test
    fun testMcpYarn() {
        runBlocking {
            yarnVersions.forEach { (mcVersion, yarnVersions) ->
                if (mcVersion !in srgVersions) return@forEach
                val map = getMcpVersionMap(mcVersion) ?: return@forEach

                map.values.forEach { versions ->
                    versions.take(5).forEach {
                        launch(Dispatchers.Default) {
                            MappingProvider.getMcp2Yarn(mcVersion, it, yarnVersions.last())
                            MappingProvider.getYarn2Mcp(mcVersion, yarnVersions.last(), it)
                        }
                    }
                }
            }
        }
    }

    private fun getMcpVersionMap(mcVersion: McVersion): Map<String, Set<MappingName.Mcp>>? {
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