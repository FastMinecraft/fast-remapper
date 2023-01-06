package dev.fastmc.remapper

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import java.io.File

class FastRemapperPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        Shared.mavenCacheDir = File(project.gradle.gradleUserHomeDir, "caches/fast-remapper/maven").also { it.mkdirs() }
        Shared.globalCacheDir = File(project.gradle.gradleUserHomeDir, "caches/fast-remapper").also { it.mkdirs() }
        val extension = project.extensions.create("fastRemapper", FastRemapperExtension::class.java).apply {
            projectCacheDir = File(project.projectDir, ".gradle/fast-remapper").also { it.mkdirs() }
        }
    }
}