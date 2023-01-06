package dev.fastmc.remapper

import dev.fastmc.remapper.mapping.MappingName
import dev.fastmc.remapper.util.McVersion
import dev.fastmc.remapper.util.flatten
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.io.Serializable
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
    
    fun register(jarTaskName: String): TaskProvider<RemapJarTask> {
        return project.tasks.register("fastRemap${jarTaskName.capitalized()}", RemapJarTask::class.java)  {
            it.setup(this, project.provider { project.tasks.named(jarTaskName, Jar::class.java) }.flatten())
        }
    }

    fun register(jarTask: Jar): TaskProvider<RemapJarTask> {
        return project.tasks.register("fastRemap${jarTask.name.capitalized()}", RemapJarTask::class.java) {
            it.setup(this, jarTask)
        }
    }

    fun register(jarTask: NamedDomainObjectProvider<out Jar>): TaskProvider<RemapJarTask> {
        return project.tasks.register("fastRemap${jarTask.name.capitalized()}", RemapJarTask::class.java) {
            it.setup(this, jarTask)
        }
    }

    enum class ProjectType : Serializable {
        FORGE, FABRIC
    }
}