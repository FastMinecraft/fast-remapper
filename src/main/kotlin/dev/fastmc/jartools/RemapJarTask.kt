package dev.fastmc.jartools

import dev.fastmc.jartools.mapping.MappingName
import dev.fastmc.jartools.mapping.MappingProvider
import dev.fastmc.jartools.mapping.mapWith
import dev.fastmc.jartools.mapping.reversed
import dev.fastmc.jartools.pipeline.GenerateRefmapStage
import dev.fastmc.jartools.pipeline.JarTaskExecutor
import dev.fastmc.jartools.pipeline.SequenceStage
import dev.fastmc.jartools.pipeline.Stage
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar
import javax.inject.Inject

abstract class RemapJarTask @Inject constructor(private val jarTask: Jar) : AbstractArchiveTask() {

    private val extension = project.extensions.getByType(JarToolsExtension::class.java)

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
                val deferredMapping = async {
                    when (extension.type) {
                        JarToolsExtension.ProjectType.FORGE -> {
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
                        JarToolsExtension.ProjectType.FABRIC -> {
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
                when (extension.type) {
                    JarToolsExtension.ProjectType.FORGE -> {
                        tasks.add(GenerateRefmapStage(deferredMapping))
                    }
                    JarToolsExtension.ProjectType.FABRIC -> {
                        tasks.add(GenerateRefmapStage(deferredMapping))
                    }
                }
                JarTaskExecutor(inputFile, outputFile).execute(SequenceStage(tasks))
            }
        }
    }
}