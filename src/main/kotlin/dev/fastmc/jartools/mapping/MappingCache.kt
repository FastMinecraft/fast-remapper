package dev.fastmc.jartools.mapping

import dev.fastmc.jartools.util.MD5Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


class MappingCache(private val dir: File) {
    private val hashFile = File(dir, ".hash")
    private val hashToFileName = Object2ObjectOpenHashMap<MD5Hash, String>()
    private val hashLock = ReentrantReadWriteLock()

    init {
        require(dir.isDirectory) { "Mapping cache directory must be a directory" }
        hashLock.write {
            hashLock.read {
                if (hashFile.exists()) {
                    hashFile.forEachLine { line ->
                        val split = line.split('=')
                        hashToFileName[MD5Hash.fromString(split[0])] = split[1]
                    }
                } else {
                    hashFile.createNewFile()
                }
            }
        }
    }

    private fun getFileName(hash: MD5Hash): String? {
        return hashLock.read {
            hashToFileName[hash]
        }
    }

    private fun putFileName(hash: MD5Hash, fileName: String) {
        hashLock.write {
            purgeOldCaches(100 - 1)
            hashToFileName[hash] = fileName
            hashFile.appendText("$hash=$fileName")
        }
    }

    @Suppress("SameParameterValue")
    private fun purgeOldCaches(capacity: Int) {
        var fileArray = dir.listFiles() ?: return
        val fileList = fileArray.filter { it.isFile && it.name != ".hash" }

        val deleteAmount = fileList.size - capacity
        if (deleteAmount <= 0) return

        fileArray = fileList.toTypedArray()
        fileArray.sortBy {
            Files.readAttributes(it.toPath(), BasicFileAttributes::class.java).creationTime().toMillis()
        }
        val toDeleteNames = hashSetOf<String>()
        for (i in 0 until deleteAmount) {
            toDeleteNames.add(fileArray[i].name)
        }

        hashLock.write {
            hashToFileName.values.removeIf {
                toDeleteNames.contains(it)
            }
            hashFile.writeText(hashToFileName.entries.joinToString("\n") { "${it.key}=${it.value}" })
            for (i in 0 until deleteAmount) {
                fileArray[i].delete()
            }
        }
    }


}