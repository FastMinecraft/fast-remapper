package dev.fastmc.remapper

import dev.fastmc.remapper.mapping.MappingName
import dev.fastmc.remapper.util.McVersion
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.jvm.tasks.Jar
import java.io.File
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class FastRemapperExtension {
    @get:Inject
    abstract val project: Project

    lateinit var projectCacheDir: File

    abstract val accessWidener: Property<String>
    abstract val mapping: Property<MappingName>
    abstract val minecraftJar: RegularFileProperty

    abstract val mixinConfigs: SetProperty<String>
    abstract val jarTaskNames: SetProperty<String>

    abstract val mcVersion: Property<McVersion>
    abstract val projectType: Property<ProjectType>

    internal val minecraftJarZipTree = project.zipTree(minecraftJar)

    init {
        mcVersion.convention(McVersion.UNKNOWN)
    }

    fun mcVersion(version: String) {
        mcVersion.set(McVersion(version))
    }

    fun mcVersion(version: McVersion) {
        mcVersion.set(version)
    }

    fun forge() {
        projectType.set(ProjectType.FORGE)
    }

    fun fabric() {
        projectType.set(ProjectType.FABRIC)
    }

    fun mixin(config: String) {
        mixinConfigs.add(config)
    }

    fun mixin(vararg configs: String) {
        mixinConfigs.addAll(*configs)
    }

    fun remap(jarTaskName: String) {
        jarTaskNames.add(jarTaskName)
    }

    fun remap(vararg jarTaskNames: String) {
        this.jarTaskNames.addAll(*jarTaskNames)
    }

    fun remap(jarTask: Jar) {
        jarTaskNames.add(jarTask.name)
    }

    fun remap(vararg jarTasks: Jar) {
        this.jarTaskNames.addAll(jarTasks.map { it.name })
    }

    fun remap(jarTask: Provider<out Jar>) {
        jarTaskNames.add(jarTask.get().name)
    }

    fun remap(vararg jarTasks: Provider<out Jar>) {
        this.jarTaskNames.addAll(jarTasks.map { it.get().name })
    }

    enum class ProjectType {
        FORGE, FABRIC
    }
}