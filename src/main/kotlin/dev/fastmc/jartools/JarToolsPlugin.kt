package dev.fastmc.jartools

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import java.io.File

class JarToolsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        Shared.globalCacheDir = File(project.gradle.gradleUserHomeDir, "caches/jar-tools").also { it.mkdirs() }
        val extension = project.extensions.create("jarTools", JarToolsExtension::class.java).apply {
            projectCacheDir = File(project.projectDir, ".gradle/jar-tools").also { it.mkdirs() }
        }

        project.afterEvaluate {
            extension.jarTasks.forEach {
                project.tasks.register("fastRemap${it.name.capitalized()}", RemapJarTask::class.java, it)
            }
        }
    }
}