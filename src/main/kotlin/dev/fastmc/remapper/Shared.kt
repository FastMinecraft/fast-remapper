package dev.fastmc.remapper

import java.io.File

object Shared {
    lateinit var mavenCacheDir: File; private set
    lateinit var globalCacheDir: File; private set

    fun init(mavenCacheDir: File, globalCacheDir: File) {
        mavenCacheDir.mkdirs()
        globalCacheDir.mkdirs()
        this.mavenCacheDir = mavenCacheDir
        this.globalCacheDir = globalCacheDir
    }
}