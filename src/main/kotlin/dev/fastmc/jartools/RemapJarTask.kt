package dev.fastmc.jartools

import dev.fastmc.jartools.mapping.*
import dev.fastmc.jartools.pipeline.*
import dev.fastmc.jartools.util.JarUtils
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.commons.Remapper
import javax.inject.Inject

abstract class RemapJarTask @Inject constructor(private val jarTask: Jar) : AbstractArchiveTask() {

    private val extension = project.extensions.getByType(FastRemapperExtension::class.java)

    init {
        destinationDirectory.set(jarTask.destinationDirectory)
        archiveBaseName.set(jarTask.archiveBaseName)
        archiveAppendix.set(jarTask.archiveAppendix)
        archiveVersion.set(jarTask.archiveVersion)
        archiveClassifier.set("remapped")
        archiveExtension.set("jar")
    }

    @get:InputFile
    val inputJar: Provider<RegularFile>
        get() = jarTask.archiveFile

    @TaskAction
    fun runTask() {
        runBlocking {
            withContext(Dispatchers.Default) {
                val inputFile = inputJar.get().asFile
                val outputFile = archiveFile.get().asFile

                val tasks = ObjectArrayList<Stage>()
                val projectMapping = extension.mapping.get()
                val mapping = getMapping(projectMapping)
                when (extension.type) {
                    FastRemapperExtension.ProjectType.FORGE -> {
                        tasks.add(GenerateRefmapStage(mapping))
                    }
                    FastRemapperExtension.ProjectType.FABRIC -> {
                        tasks.add(GenerateRefmapStage(mapping))
                    }
                }
                tasks.add(object : RemapStage() {
                    override suspend fun remapper(classEntries: Collection<ClassEntry>): Remapper {
                        return MixinRemapper(mapping.get(classEntries), classEntries)
                    }
                })
                JarTaskExecutor(inputFile, outputFile).execute(SequenceStage(tasks))
            }
        }
    }

    private fun CoroutineScope.getMapping(projectMapping: MappingName): MappingPipeline {
        val minecraftClasses = async(Dispatchers.IO) {
            JarUtils.unpackFlow(extension.minecraftJar.get().asFile)
                .filter { it.second != null }
                .filter { it.first.endsWith(".class") }
                .map { ClassEntry(it.first, it.second!!) }
                .toList()
        }
        return SequenceMappingPipeline(
            MappingProviderPipeline(getBaseMapping(projectMapping)),
            MixinMappingPipeline(),
            SubclassMappingPipeline(minecraftClasses)
        )
    }

    private fun CoroutineScope.getBaseMapping(projectMapping: MappingName): Deferred<ClassMapping> {
        return async {
            when (extension.type) {
                FastRemapperExtension.ProjectType.FORGE -> {
                    when (projectMapping) {
                        is MappingName.Mcp -> {
                            MappingProvider.getOrCompute(
                                extension.mcVersion,
                                projectMapping,
                                MappingName.Searge
                            ) {
                                MappingProvider.Searge2Mcp.provide(
                                    extension.mcVersion,
                                    projectMapping
                                ).reversed()
                            }
                        }
                        is MappingName.Yarn -> {
                            MappingProvider.getOrCompute(
                                extension.mcVersion,
                                projectMapping,
                                MappingName.Searge
                            ) {
                                MappingProvider.YarnIntermediary2Yarn.provide(
                                    extension.mcVersion,
                                    projectMapping
                                ).reversed().mapWith(
                                    MappingProvider.Obf2YarnIntermediary.provide(
                                        extension.mcVersion
                                    ).reversed()
                                ).mapWith(
                                    MappingProvider.Obf2Searge.provide(extension.mcVersion)
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
                                extension.mcVersion,
                                projectMapping,
                                MappingName.YarnIntermediary
                            ) {
                                MappingProvider.YarnIntermediary2Yarn.provide(
                                    extension.mcVersion,
                                    projectMapping
                                ).reversed()
                            }
                        }
                        is MappingName.Mcp -> {
                            MappingProvider.getOrCompute(
                                extension.mcVersion,
                                projectMapping,
                                MappingName.YarnIntermediary
                            ) {
                                MappingProvider.Searge2Mcp.provide(
                                    extension.mcVersion,
                                    projectMapping
                                ).reversed().mapWith(
                                    MappingProvider.Obf2Searge.provide(extension.mcVersion).reversed()
                                ).mapWith(
                                    MappingProvider.Obf2YarnIntermediary.provide(extension.mcVersion)
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