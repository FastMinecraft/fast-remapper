package dev.fastmc.remapper

import dev.fastmc.remapper.mapping.*
import dev.fastmc.remapper.pipeline.*
import dev.fastmc.remapper.util.JarUtils
import dev.fastmc.remapper.util.McVersion
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.Remapper
import java.io.File

abstract class RemapJarTask : Jar() {
    @get:Input
    internal abstract val projectType: Property<FastRemapperExtension.ProjectType>
    
    @get:Input
    internal abstract val mcVersion: Property<McVersion>
    
    @get:Input
    internal abstract val mapping: Property<MappingName>

    @get:InputFiles
    internal abstract val minecraftJarZipTree: Property<FileTree>
    
    @get:Input
    internal abstract val mixinConfigs: SetProperty<String>

    internal fun setup(extension: FastRemapperExtension, jarTask: Provider<out Jar>) {
        setup(extension)
        dependsOn(jarTask)

        destinationDirectory.set(jarTask.flatMap { it.destinationDirectory })
        archiveBaseName.set(jarTask.flatMap { it.archiveBaseName })
        archiveAppendix.set(jarTask.flatMap { it.archiveAppendix })
        archiveVersion.set(jarTask.flatMap { it.archiveVersion })
        archiveClassifier.set("remapped")
        archiveExtension.set("jar")

        val jarZipTree = project.provider {
            project.zipTree(jarTask.flatMap { it.archiveFile })
        }

        from(jarZipTree)

        manifest.from(jarZipTree.map { zipTree -> zipTree.find { it.name == "MANIFEST.MF" }!! })
        manifest.attributes(
            mapOf(
                "MixinConfigs" to mixinConfigs.get().joinToString(", "),
            )
        )
    }

    internal fun setup(extension: FastRemapperExtension, jarTask: Jar) {
        setup(extension)
        dependsOn(jarTask)

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
                "MixinConfigs" to mixinConfigs.get().joinToString(", "),
            )
        )
    }

    private fun setup(extension: FastRemapperExtension) {
            projectType.set(extension.projectType)
            mcVersion.set(extension.mcVersion)
            mapping.set(extension.mapping)
            minecraftJarZipTree.set(extension.minecraftJarZipTree)
            mixinConfigs.set(extension.mixinConfigs)
    }

    override fun createCopyAction(): CopyAction {
        return RemapJarAction(this)
    }

    class RemapJarAction(task: RemapJarTask) : CopyAction {
        private val projectType = task.projectType.get()
        private val mcVersion = task.mcVersion.get()
        private val mapping = task.mapping.get()
        private val minecraftJarZipTree = task.minecraftJarZipTree.get()
        private val mixinConfigs = task.mixinConfigs.get().toList()
        private val outputFile = task.archiveFile.get().asFile
        
        
        private fun CoroutineScope.getMapping(projectMapping: MappingName): MappingPipeline {
            val minecraftClassFiles = ObjectArrayList<Pair<String, File>>()
            minecraftJarZipTree.visit {
                if (it.isDirectory) return@visit
                if (!it.name.endsWith(".class")) return@visit
                minecraftClassFiles.add(it.relativePath.pathString to it.file)
            }
            val minecraftClasses = async(Dispatchers.IO) {
                minecraftClassFiles.map { (path, file) ->
                    ClassEntry(
                        path,
                        file.readBytes(),
                        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
                    )
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
                val mapping = getMapping(mapping)
                val refmapBaseName = outputFile.name.removeSuffix(".jar")
                when (projectType) {
                    FastRemapperExtension.ProjectType.FORGE -> {
                        tasks.add(
                            GenerateRefmapStage(
                                mapping,
                                refmapBaseName,
                                "searge",
                                mixinConfigs.toList()
                            )
                        )
                    }
                    FastRemapperExtension.ProjectType.FABRIC -> {
                        tasks.add(
                            GenerateRefmapStage(
                                mapping,
                                refmapBaseName,
                                "named:intermediary",
                                mixinConfigs.toList()
                            )
                        )
                    }
                    else -> {
                        throw IllegalArgumentException("Unknown project type: $projectType")
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
                when (projectType) {
                    FastRemapperExtension.ProjectType.FORGE -> {
                        when (projectMapping) {
                            is MappingName.Mcp -> {
                                MappingProvider.getOrCompute(
                                    mcVersion,
                                    projectMapping,
                                    MappingName.Searge
                                ) {
                                    MappingProvider.Searge2Mcp.provide(mcVersion, projectMapping).reversed()
                                }
                            }
                            is MappingName.Yarn -> {
                                MappingProvider.getOrCompute(
                                    mcVersion,
                                    projectMapping,
                                    MappingName.Searge
                                ) {
                                    MappingProvider.YarnIntermediary2Yarn.provide(mcVersion, projectMapping).reversed()
                                        .mapWith(MappingProvider.Obf2YarnIntermediary.provide(mcVersion).reversed())
                                        .mapWith(MappingProvider.Obf2Searge.provide(mcVersion))
                                }
                            }
                            else -> throw IllegalArgumentException("Unsupported mapping for forge project: $projectMapping")
                        }
                    }
                    FastRemapperExtension.ProjectType.FABRIC -> {
                        when (projectMapping) {
                            is MappingName.Yarn -> {
                                MappingProvider.getOrCompute(
                                    mcVersion,
                                    projectMapping,
                                    MappingName.YarnIntermediary
                                ) {
                                    MappingProvider.YarnIntermediary2Yarn.provide(mcVersion, projectMapping).reversed()
                                }
                            }
                            is MappingName.Mcp -> {
                                MappingProvider.getOrCompute(
                                    mcVersion,
                                    projectMapping,
                                    MappingName.YarnIntermediary
                                ) {
                                    MappingProvider.Searge2Mcp.provide(mcVersion, projectMapping).reversed()
                                        .mapWith(MappingProvider.Obf2Searge.provide(mcVersion).reversed())
                                        .mapWith(MappingProvider.Obf2YarnIntermediary.provide(mcVersion))
                                }
                            }
                            else -> throw IllegalArgumentException("Unsupported mapping for fabric project: $projectMapping")
                        }
                    }
                    else -> throw IllegalArgumentException("Unknown project type: $projectType")
                }
            }
        }

    }
}