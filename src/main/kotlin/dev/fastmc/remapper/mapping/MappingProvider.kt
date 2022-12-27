package dev.fastmc.remapper.mapping

import dev.fastmc.remapper.Shared
import dev.fastmc.remapper.util.GlobalMavenCache
import dev.fastmc.remapper.util.McVersion
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        @Suppress("BlockingMethodInNonBlockingContext")
        override suspend fun provide(mcVersion: McVersion, mappingName: MappingName.Mcp): ClassMapping {
            return coroutineScope {
                getOrCompute(mcVersion, MappingName.Searge, mappingName) {
                    val deferred = async {
                        val url = getMcpUrl(mcVersion, mappingName)
                        val fields = Object2ObjectOpenHashMap<String, String>()
                        val methods = Object2ObjectOpenHashMap<String, String>()

                        val file = GlobalMavenCache.getMaven(url).await()!!

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

        private fun getMcpUrl(mcVersion: McVersion, mappingName: MappingName.Mcp): String {
            var mcVersionOverride = mcVersion
            if (mcVersion.one == 1 && mcVersion.two == 12) {
                mcVersionOverride = McVersion("1.12")
            }
            return "https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_${mappingName.channel}/${mappingName.version}-$mcVersionOverride/mcp_${mappingName.channel}-${mappingName.version}-$mcVersionOverride.zip"
        }
    }

    companion object {
        val cacheDir = File(Shared.globalCacheDir, "mappings").also { it.mkdirs() }

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
            return if (file.exists()) {
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
}