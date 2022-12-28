package dev.fastmc.remapper.mapping

import com.google.gson.JsonParser
import dev.fastmc.remapper.Shared
import dev.fastmc.remapper.util.GlobalMavenCache
import dev.fastmc.remapper.util.McVersion
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.zip.ZipFile

sealed interface MappingProvider<T : MappingName> {
    suspend fun provide(mcVersion: McVersion, mappingName: T): ClassMapping

    object Obf2YarnIntermediary : MappingProvider<MappingName.YarnIntermediary> {
        override suspend fun provide(mcVersion: McVersion, mappingName: MappingName.YarnIntermediary): ClassMapping {
            return getOrCompute(mcVersion, MappingName.Obfuscated, mappingName) {
                val url = if (mcVersion < McVersion("1.14")) {
                    "https://maven.legacyfabric.net/net/legacyfabric/intermediary/${mcVersion}/intermediary-${mcVersion}-v2.jar"
                } else {
                    "https://maven.fabricmc.net/net/fabricmc/intermediary/${mcVersion}/intermediary-${mcVersion}-v2.jar"
                }

                val file = GlobalMavenCache.getMaven(url).await()!!

                @Suppress("BlockingMethodInNonBlockingContext")
                val string = ZipFile(file).use {
                    val entry = it.getEntry("mappings/mappings.tiny")
                    it.getInputStream(entry).readAllBytes().toString(Charsets.UTF_8)
                }
                ExternalMappingParser.TINY.parse(string.reader())
            }
        }

        suspend fun provide(mcVersion: McVersion): ClassMapping {
            return provide(mcVersion, MappingName.YarnIntermediary)
        }
    }

    object YarnIntermediary2Yarn : MappingProvider<MappingName.Yarn> {
        override suspend fun provide(mcVersion: McVersion, mappingName: MappingName.Yarn): ClassMapping {
            return getOrCompute(mcVersion, MappingName.YarnIntermediary, mappingName) {
                val url = if (mcVersion < McVersion("1.14")) {
                    "https://maven.legacyfabric.net/net/legacyfabric/yarn/${mcVersion}+build.${mappingName.buildNumber}/yarn-${mcVersion}+build.${mappingName.buildNumber}-v2.jar"
                } else {
                    "https://maven.fabricmc.net/net/fabricmc/yarn/${mcVersion}+build.${mappingName.buildNumber}/yarn-${mcVersion}+build.${mappingName.buildNumber}-v2.jar"
                }
                val file = GlobalMavenCache.getMaven(url).await()!!

                @Suppress("BlockingMethodInNonBlockingContext")
                val string = ZipFile(file).use {
                    val entry = it.getEntry("mappings/mappings.tiny")
                    it.getInputStream(entry).readAllBytes().toString(Charsets.UTF_8)
                }
                ExternalMappingParser.TINY.parse(string)
            }
        }
    }

    object Obf2Searge : MappingProvider<MappingName.Searge> {
        override suspend fun provide(mcVersion: McVersion, mappingName: MappingName.Searge): ClassMapping {
            return getOrCompute(mcVersion, MappingName.Obfuscated, mappingName) {
                val url = if (mcVersion < McVersion("1.12.2")) {
                    throw UnsupportedOperationException("MCP is not supported for versions below 1.12.2")
//                    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/${mcVersion}/mcp-${mcVersion}-srg.zip"
                } else {
                    "https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/${mcVersion}/mcp_config-${mcVersion}.zip"
                }
                val file = GlobalMavenCache.getMaven(url).await()!!

                @Suppress("BlockingMethodInNonBlockingContext")
                val string = ZipFile(file).use {
                    val entry = it.getEntry("config/joined.tsrg")
                    it.getInputStream(entry).readAllBytes().toString(Charsets.UTF_8)
                }
                ExternalMappingParser.TSRG.parse(string)
            }
        }

        suspend fun provide(mcVersion: McVersion): ClassMapping {
            return provide(mcVersion, MappingName.Searge)
        }
    }

    object Searge2Mcp : MappingProvider<MappingName.Mcp> {
        override suspend fun provide(mcVersion: McVersion, mappingName: MappingName.Mcp): ClassMapping {
            return coroutineScope {
                getOrCompute(mcVersion, MappingName.Searge, mappingName) {
                    val deferred = async {
                        val url = getMcpUrl(mcVersion, mappingName)
                        val fields = Object2ObjectOpenHashMap<String, String>()
                        val methods = Object2ObjectOpenHashMap<String, String>()

                        val file = GlobalMavenCache.getMaven(url).await()
                            ?: throw IllegalArgumentException("Failed to download MCP config $mcVersion-$mappingName")

                        ZipFile(file).use { zipFile ->
                            val fieldEntry = zipFile.getEntry("fields.csv")
                            zipFile.getInputStream(fieldEntry).bufferedReader().forEachLine {
                                val split = it.split(',')
                                fields[split[0]] = split[1]
                            }
                            val methodEntry = zipFile.getEntry("methods.csv")
                            zipFile.getInputStream(methodEntry).bufferedReader().forEachLine {
                                val split = it.split(',')
                                methods[split[0]] = split[1]
                            }
                        }
                        fields to methods
                    }
                    val obf2Srg = Obf2Searge.provide(mcVersion)
                    val (srg2mcpFields, srg2mcpMethods) = deferred.await()

                    val result = MutableClassMapping()

                    result.backingMap.ensureCapacity(obf2Srg.size)
                    obf2Srg.backingMap.forEachFast { classEntry ->
                        result.getOrCreate(classEntry.nameTo).apply {
                            this.fieldMapping.backingMap.ensureCapacity(classEntry.fieldMapping.size)
                            classEntry.fieldMapping.backingMap.forEachFast { fieldEntry ->
                                this.fieldMapping.add(
                                    MappingEntry.Field(
                                        fieldEntry.nameTo,
                                        srg2mcpFields[fieldEntry.nameTo] ?: fieldEntry.nameTo
                                    )
                                )
                            }
                            this.methodMapping.backingMap.ensureCapacity(classEntry.methodMapping.size)
                            classEntry.methodMapping.backingMap.forEachFast { methodEntry ->
                                this.methodMapping.add(
                                    MappingEntry.Method(
                                        methodEntry.nameTo,
                                        obf2Srg.remapDesc(methodEntry.desc),
                                        srg2mcpMethods[methodEntry.nameTo] ?: methodEntry.nameTo
                                    )
                                )
                            }
                        }
                    }

                    result.asImmutable()
                }
            }
        }

        private suspend fun getMcpUrl(mcVersion: McVersion, mappingName: MappingName.Mcp): String {
            val mcpVersionUrl = "https://files.minecraftforge.net/maven/de/oceanlabs/mcp/versions.json"
            val mcpVersionFile = GlobalMavenCache.getMaven(mcpVersionUrl).await()!!
            val mcpVersion = JsonParser.parseString(mcpVersionFile.readText()).asJsonObject.asMap()

            var mcVersionOverride = mcVersion
            val obj = mcpVersion[mcVersionOverride.toString()]?.asJsonObject
            if (obj == null) {
                mcVersionOverride = McVersion(mcVersionOverride.one, mcVersionOverride.two)
            }

            return "https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_${mappingName.channel}/${mappingName.version}-$mcVersionOverride/mcp_${mappingName.channel}-${mappingName.version}-$mcVersionOverride.zip"
        }
    }

    companion object {
        val cacheDir = File(Shared.globalCacheDir, "mappings").also { it.mkdirs() }
        val locks = Object2ObjectMaps.synchronize(Object2ObjectOpenHashMap<String, Mutex>())

        fun getFile(mcVersion: McVersion, from: MappingName, to: MappingName): File {
            return File(cacheDir, "$mcVersion-$from-$to.mapping")
        }

        suspend inline fun getOrCompute(
            mcVersion: McVersion,
            from: MappingName,
            to: MappingName,
            crossinline compute: suspend () -> ClassMapping
        ): ClassMapping {
            val file = getFile(mcVersion, from, to)
            val mutex = locks.computeIfAbsent(file.name) { _: String ->
                Mutex()
            }
            return mutex.withLock {
                if (file.exists()) {
                    file.reader().use { InternalMappingParser.read(it) }
                } else {
                    val mapping = compute()
                    file.writer().use {
                        InternalMappingParser.write(it, mapping)
                    }
                    mapping
                }
            }
        }

        suspend fun getObf2Srg(mcVersion: McVersion): ClassMapping {
            return Obf2Searge.provide(mcVersion)
        }

        suspend fun getSrg2Obf(mcVersion: McVersion): ClassMapping {
            return getOrCompute(mcVersion, MappingName.Searge, MappingName.Obfuscated) {
                getObf2Srg(mcVersion).reversed()
            }
        }

        suspend fun getSrg2Mcp(mcVersion: McVersion, mcp: MappingName.Mcp): ClassMapping {
            return Searge2Mcp.provide(mcVersion, mcp)
        }

        suspend fun getMcp2Srg(mcVersion: McVersion, mcp: MappingName.Mcp): ClassMapping {
            return getOrCompute(mcVersion, mcp, MappingName.Searge) {
                getSrg2Mcp(mcVersion, mcp).reversed()
            }
        }

        suspend fun getObf2Mcp(mcVersion: McVersion, mcp: MappingName.Mcp): ClassMapping {
            return getOrCompute(mcVersion, MappingName.Obfuscated, mcp) {
                Obf2Searge.provide(mcVersion).mapWith(Searge2Mcp.provide(mcVersion, mcp))
            }
        }

        suspend fun getMcp2Obf(mcVersion: McVersion, mcp: MappingName.Mcp): ClassMapping {
            return getOrCompute(mcVersion, mcp, MappingName.Obfuscated) {
                getObf2Mcp(mcVersion, mcp).reversed()
            }
        }

        suspend fun getObf2Intermediary(mcVersion: McVersion): ClassMapping {
            return Obf2YarnIntermediary.provide(mcVersion)
        }

        suspend fun getIntermediary2Obf(mcVersion: McVersion): ClassMapping {
            return getOrCompute(mcVersion, MappingName.YarnIntermediary, MappingName.Obfuscated) {
                getObf2Intermediary(mcVersion).reversed()
            }
        }

        suspend fun getIntermediary2Yarn(mcVersion: McVersion, yarn: MappingName.Yarn): ClassMapping {
            return YarnIntermediary2Yarn.provide(mcVersion, yarn)
        }

        suspend fun getYarn2Intermediary(mcVersion: McVersion, yarn: MappingName.Yarn): ClassMapping {
            return getOrCompute(mcVersion, yarn, MappingName.YarnIntermediary) {
                getIntermediary2Yarn(mcVersion, yarn).reversed()
            }
        }

        suspend fun getObf2Yarn(mcVersion: McVersion, yarn: MappingName.Yarn): ClassMapping {
            return getOrCompute(mcVersion, MappingName.Obfuscated, yarn) {
                getObf2Intermediary(mcVersion).mapWith(getIntermediary2Yarn(mcVersion, yarn))
            }
        }

        suspend fun getYarn2Obf(mcVersion: McVersion, yarn: MappingName.Yarn): ClassMapping {
            return getOrCompute(mcVersion, yarn, MappingName.Obfuscated) {
                getObf2Yarn(mcVersion, yarn).reversed()
            }
        }

        suspend fun getSrg2Intermediary(mcVersion: McVersion): ClassMapping {
            return getOrCompute(mcVersion, MappingName.Searge, MappingName.YarnIntermediary) {
                getSrg2Obf(mcVersion).mapWith(getObf2Intermediary(mcVersion))
            }
        }

        suspend fun getIntermediary2Srg(mcVersion: McVersion): ClassMapping {
            return getOrCompute(mcVersion, MappingName.YarnIntermediary, MappingName.Searge) {
                getSrg2Intermediary(mcVersion).reversed()
            }
        }

        suspend fun getSrg2Yarn(mcVersion: McVersion, yarn: MappingName.Yarn): ClassMapping {
            return getOrCompute(mcVersion, MappingName.Searge, yarn) {
                getSrg2Intermediary(mcVersion).mapWith(getIntermediary2Yarn(mcVersion, yarn))
            }
        }

        suspend fun getYarn2Srg(mcVersion: McVersion, yarn: MappingName.Yarn): ClassMapping {
            return getOrCompute(mcVersion, yarn, MappingName.Searge) {
                getSrg2Yarn(mcVersion, yarn).reversed()
            }
        }

        suspend fun getMcp2Intermediary(mcVersion: McVersion, mcp: MappingName.Mcp): ClassMapping {
            return getOrCompute(mcVersion, mcp, MappingName.YarnIntermediary) {
                getMcp2Obf(mcVersion, mcp).mapWith(getObf2Intermediary(mcVersion))
            }
        }

        suspend fun getIntermediary2Mcp(mcVersion: McVersion, mcp: MappingName.Mcp): ClassMapping {
            return getOrCompute(mcVersion, MappingName.YarnIntermediary, mcp) {
                getMcp2Intermediary(mcVersion, mcp).reversed()
            }
        }

        suspend fun getMcp2Yarn(mcVersion: McVersion, mcp: MappingName.Mcp, yarn: MappingName.Yarn): ClassMapping {
            return getOrCompute(mcVersion, mcp, yarn) {
                getMcp2Obf(mcVersion, mcp).mapWith(getObf2Yarn(mcVersion, yarn))
            }
        }

        suspend fun getYarn2Mcp(mcVersion: McVersion, yarn: MappingName.Yarn, mcp: MappingName.Mcp): ClassMapping {
            return getOrCompute(mcVersion, yarn, mcp) {
                getMcp2Yarn(mcVersion, mcp, yarn).reversed()
            }
        }
    }
}