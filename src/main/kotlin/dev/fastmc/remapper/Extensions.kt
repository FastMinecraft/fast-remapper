package dev.fastmc.remapper

import dev.fastmc.remapper.mapping.MappingName
import dev.fastmc.remapper.util.McVersion
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.jvm.tasks.Jar
import java.io.File

abstract class FastRemapperExtension {
    lateinit var projectCacheDir: File

    abstract val accessWidener: Property<String>
    abstract val mapping: Property<MappingName>
    abstract val minecraftJar: RegularFileProperty

    var mcVersion = McVersion.UNKNOWN; private set
    lateinit var type: ProjectType; private set
    private val mixinConfigs0 = ObjectArrayList<String>()
    private val jarTasks0 = ObjectArrayList<Jar>()

    val mixinConfigs: List<String> get() = mixinConfigs0
    val jarTasks: List<Jar> get() = jarTasks0

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

    fun remap(jarTask: Jar) {
        jarTasks0.add(jarTask)
    }

    fun remap(vararg jarTasks: Jar) {
        this.jarTasks0.addAll(jarTasks)
    }

    fun remap(jarTasks: Provider<Jar>) {
        this.jarTasks0.add(jarTasks.get())
    }

    fun remap(vararg jarTasks: Provider<Jar>) {
        this.jarTasks0.addAll(jarTasks.map { it.get() })
    }

    enum class ProjectType {
        FORGE, FABRIC
    }
}