package dev.fastmc.remapper.util

import dev.fastmc.remapper.Shared
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URL

object GlobalMavenCache {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val cacheDir get() = Shared.mavenCacheDir
    private val locks = Object2ObjectMaps.synchronize(Object2ObjectOpenHashMap<String, Mutex>())

    private val mavenRegex = "maven.*?/(.*)".toRegex()

    suspend fun getMaven(url: String): Deferred<File?> {
        check(url.startsWith("http")) { "URL must start with http" }
        check(url.contains(mavenRegex)) { "URL must contain maven" }
        val localPath = mavenRegex.find(url)!!.groupValues[1]

        val local = File(cacheDir, localPath)
        val mutex = locks.computeIfAbsent(localPath) { _: String -> Mutex() }
        return mutex.withLock {
            if (local.exists()) {
                CompletableDeferred(local)
            } else {
                scope.async(Dispatchers.IO) {
                    URL(url).openStream().use { input ->
                        local.parentFile.mkdirs()
                        local.createNewFile()
                        local.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    local
                }
            }
        }
    }
}