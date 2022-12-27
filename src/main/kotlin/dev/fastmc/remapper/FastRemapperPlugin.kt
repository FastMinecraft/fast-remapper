package dev.fastmc.remapper

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import java.io.File

class FastRemapperPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        Shared.globalCacheDir = File(project.gradle.gradleUserHomeDir, "caches/jar-tools").also { it.mkdirs() }
        val extension = project.extensions.create("fastRemapper", FastRemapperExtension::class.java).apply {
            projectCacheDir = File(project.projectDir, ".gradle/jar-tools").also { it.mkdirs() }
        }

        project.afterEvaluate {
            extension.jarTaskNames.forEach {
                val task = project.tasks.getByName(it)
                project.tasks.register("fastRemap${task.name.capitalized()}", RemapJarTask::class.java, task)
            }
        }
    }
}