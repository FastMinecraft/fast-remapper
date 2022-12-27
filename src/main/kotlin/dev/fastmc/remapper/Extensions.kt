package dev.fastmc.remapper

import dev.fastmc.remapper.mapping.MappingName
import dev.fastmc.remapper.util.McVersion
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.jvm.tasks.Jar
import java.io.File
import javax.inject.Inject

abstract class FastRemapperExtension {
    @get:Inject
    abstract val project: Project

    lateinit var projectCacheDir: File

    abstract val accessWidener: Property<String>
    abstract val mapping: Property<MappingName>
    abstract val minecraftJar: RegularFileProperty

    var mcVersion = McVersion.UNKNOWN; private set
    lateinit var type: ProjectType; private set
    private val mixinConfigs0 = ObjectArrayList<String>()
    private val jarTaskNames0 = ObjectArrayList<String>()

    val mixinConfigs: List<String> get() = mixinConfigs0
    val jarTaskNames: List<String> get() = jarTaskNames0

    fun mcVersion(version: String) {
        mcVersion = McVersion(version)
    }

    fun mcVersion(version: McVersion) {
        mcVersion = version
    }

    fun forge() {
        type = ProjectType.FORGE
    }

    fun fabric() {
        type = ProjectType.FABRIC
    }

    fun mixin(config: String) {
        mixinConfigs0.add(config)
    }

    fun mixin(vararg configs: String) {
        mixinConfigs0.addAll(configs)
    }

    fun remap(jarTaskName: String) {
        jarTaskNames0.add(jarTaskName)
    }

    fun remap(vararg jarTaskNames: String) {
        this.jarTaskNames0.addAll(jarTaskNames)
    }

    fun remap(jarTask: Jar) {
        jarTaskNames0.add(jarTask.name)
    }

    fun remap(vararg jarTasks: Jar) {
        this.jarTaskNames0.addAll(jarTasks.map { it.name })
    }

    enum class ProjectType {
        FORGE, FABRIC
    }
}