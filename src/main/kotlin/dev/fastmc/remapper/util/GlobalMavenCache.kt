package dev.fastmc.remapper.util

import dev.fastmc.remapper.Shared
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import java.net.URL

object GlobalMavenCache {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val cacheDir by lazy { File(Shared.globalCacheDir, "maven") }

    private val mavenRegex = "maven.*?/(.*)".toRegex()

    fun getMaven(url: String): Deferred<File?> {
        check(url.startsWith("http")) { "URL must start with http" }
        check(url.contains(mavenRegex)) { "URL must contain maven" }
        val localPath = mavenRegex.find(url)!!.groupValues[1]

        val local = File(cacheDir, localPath)
        if (local.exists()) {
            return CompletableDeferred(local)
        }
        return scope.async(Dispatchers.IO) {
            try {
                URL(url).openStream().use { input ->
                    local.parentFile.mkdirs()
                    local.createNewFile()
                    local.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                local
            } catch (e: FileNotFoundException) {
                null
            }
        }
    }
}