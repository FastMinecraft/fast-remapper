package dev.fastmc.remapper

import dev.fastmc.remapper.mapping.*
import dev.fastmc.remapper.pipeline.*
import dev.fastmc.remapper.util.JarUtils
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.Remapper
import java.io.File
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class RemapJarTask @Inject constructor(jarTask: Jar) : Jar() {
    private val extension = project.extensions.getByType(FastRemapperExtension::class.java)

    init {
        destinationDirectory.set(jarTask.destinationDirectory)
        archiveBaseName.set(jarTask.archiveBaseName)
        archiveAppendix.set(jarTask.archiveAppendix)
        archiveVersion.set(jarTask.archiveVersion)
        archiveClassifier.set("remapped")
        archiveExtension.set("jar")

        val jarZipTree = project.provider {
            project.zipTree(jarTask.archiveFile)
        }

        from(jarZipTree)

        manifest.from(jarZipTree.map { zipTree -> zipTree.find { it.name == "MANIFEST.MF" }!! })
        manifest.attributes(
            mapOf(
                "MixinConfigs" to extension.mixinConfigs.get().joinToString(", "),
            )
        )
    }

    override fun createCopyAction(): CopyAction {
        return RemapJarAction(extension, archiveFile.get().asFile)
    }

    class RemapJarAction(private val extension: FastRemapperExtension, private val outputFile: File) : CopyAction {
        private fun CoroutineScope.getMapping(projectMapping: MappingName): MappingPipeline {
            val minecraftClassFiles = ObjectArrayList<Pair<String, File>>()
            extension.minecraftJarZipTree.visit {
                if (it.isDirectory) return@visit
                if (!it.name.endsWith(".class")) return@visit
                minecraftClassFiles.add(it.relativePath.pathString to it.file)
            }
            val minecraftClasses = async(Dispatchers.IO) {
                minecraftClassFiles.map { (path, file) ->
                    ClassEntry(path, file.readBytes(), ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                }
            }
            return SequenceMappingPipeline(
                MappingProviderPipeline(getBaseMapping(projectMapping)),
                MixinMappingPipeline(),
                SubclassMappingPipeline(minecraftClasses)
            )
        }

        override fun execute(stream: CopyActionProcessingStream): WorkResult {
            runBlocking {
                val tasks = ObjectArrayList<Stage>()
                val projectMapping = extension.mapping.get()
                val mapping = getMapping(projectMapping)
                val refmapBaseName = outputFile.name.removeSuffix(".jar")
                when (extension.projectType.get()) {
                    FastRemapperExtension.ProjectType.FORGE -> {
                        tasks.add(GenerateRefmapStage(mapping, refmapBaseName, "searge", extension.mixinConfigs.get().toList()))
                    }
                    FastRemapperExtension.ProjectType.FABRIC -> {
                        tasks.add(
                            GenerateRefmapStage(
                                mapping,
                                refmapBaseName,
                                "named:intermediary",
                                extension.mixinConfigs.get().toList()
                            )
                        )
                    }
                    else -> {
                        throw IllegalArgumentException("Unknown project type: ${extension.projectType.get()}")
                    }
                }
                tasks.add(object : RemapStage() {
                    override suspend fun remapper(classEntries: Collection<ClassEntry>): Remapper {
                        return MixinRemapper(mapping.get(classEntries), classEntries)
                    }
                })

                withContext(Dispatchers.Default) {
                    val cachedFileFlow = JarUtils.unpackFlow(CoroutineScope(coroutineContext + Dispatchers.IO), stream)

                    val jarEntryFlow = cachedFileFlow.map {
                        when {
                            it.second == null -> JarEntry(it.first)
                            it.first.endsWith(".class") -> ClassEntry(it.first, it.second!!)
                            else -> JarFileEntry(it.first, it.second!!)
                        }
                    }

                    val files = Object2ObjectOpenHashMap<String, JarEntry>()
                    jarEntryFlow.collect {
                        files[it.fileName] = it
                    }
                    val result = SequenceStage(tasks).run(files)

                    withContext(Dispatchers.IO) {
                        val output = flow {
                            result.values.forEach {
                                emit(it.fileName to (it as? JarFileEntry)?.bytes)
                            }
                        }
                        JarUtils.repackFlow(output, outputFile)
                    }
                }
            }

            return WorkResults.didWork(true)
        }

        private fun CoroutineScope.getBaseMapping(projectMapping: MappingName): Deferred<ClassMapping> {
            return async {
                when (extension.projectType.get()) {
                    FastRemapperExtension.ProjectType.FORGE -> {
                        when (projectMapping) {
                            is MappingName.Mcp -> {
                                MappingProvider.getOrCompute(
                                    extension.mcVersion.get(),
                                    projectMapping,
                                    MappingName.Searge
                                ) {
                                    MappingProvider.Searge2Mcp.provide(
                                        extension.mcVersion.get(),
                                        projectMapping
                                    ).reversed()
                                }
                            }
                            is MappingName.Yarn -> {
                                MappingProvider.getOrCompute(
                                    extension.mcVersion.get(),
                                    projectMapping,
                                    MappingName.Searge
                                ) {
                                    MappingProvider.YarnIntermediary2Yarn.provide(
                                        extension.mcVersion.get(),
                                        projectMapping
                                    ).reversed().mapWith(
                                        MappingProvider.Obf2YarnIntermediary.provide(
                                            extension.mcVersion.get()
                                        ).reversed()
                                    ).mapWith(
                                        MappingProvider.Obf2Searge.provide(extension.mcVersion.get())
                                    )
                                }
                            }
                            else -> throw IllegalArgumentException("Unsupported mapping for forge project: $projectMapping")
                        }
                    }
                    FastRemapperExtension.ProjectType.FABRIC -> {
                        when (projectMapping) {
                            is MappingName.Yarn -> {
                                MappingProvider.getOrCompute(
                                    extension.mcVersion.get(),
                                    projectMapping,
                                    MappingName.YarnIntermediary
                                ) {
                                    MappingProvider.YarnIntermediary2Yarn.provide(
                                        extension.mcVersion.get(),
                                        projectMapping
                                    ).reversed()
                                }
                            }
                            is MappingName.Mcp -> {
                                MappingProvider.getOrCompute(
                                    extension.mcVersion.get(),
                                    projectMapping,
                                    MappingName.YarnIntermediary
                                ) {
                                    MappingProvider.Searge2Mcp.provide(
                                        extension.mcVersion.get(),
                                        projectMapping
                                    ).reversed().mapWith(
                                        MappingProvider.Obf2Searge.provide(extension.mcVersion.get()).reversed()
                                    ).mapWith(
                                        MappingProvider.Obf2YarnIntermediary.provide(extension.mcVersion.get())
                                    )
                                }
                            }
                            else -> throw IllegalArgumentException("Unsupported mapping for fabric project: $projectMapping")
                        }
                    }
                }
            }
        }

    }
}