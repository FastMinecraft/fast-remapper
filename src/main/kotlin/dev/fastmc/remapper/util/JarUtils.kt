package dev.fastmc.remapper.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object JarUtils {
    suspend fun readClassNodes(input: File): List<ClassNode> {
        return coroutineScope {
            val unpacked = unpackFlow(input) {
                !it.isDirectory && it.name.endsWith(".class")
            }.flowOn(Dispatchers.IO)
            return@coroutineScope unpacked.map {
                async {
                    val classReader = ClassReader(it.second)
                    val classNode = ClassNode()
                    classReader.accept(
                        classNode,
                        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
                    )
                    classNode
                }
            }.toList().awaitAll()
        }
    }

    inline fun unpackFlow(
        input: File,
        crossinline predicate: (ZipEntry) -> Boolean
    ): Flow<Pair<String, ByteArray?>> {
        return flow {
            ZipInputStream(input.inputStream().buffered(1024 * 1024)).use { stream ->
                while (true) {
                    val entry = stream.nextEntry ?: break
                    if (!predicate(entry)) continue
                    emit(entry.name to (if (entry.isDirectory) null else stream.readBytes()))
                }
            }
        }
    }

    fun unpackFlow(input: File): Flow<Pair<String, ByteArray?>> {
        return flow {
            ZipInputStream(input.inputStream().buffered(1024 * 1024)).use { stream ->
                while (true) {
                    val entry = stream.nextEntry ?: break
                    emit(
                        entry.name to (if (entry.isDirectory) {
                            null
                        } else {
                            val size = entry.size.toInt()
                            if (size == -1) stream.readBytes() else stream.readNBytes(size)
                        })
                    )
                }
            }
        }
    }

    suspend fun repackFlow(files: Flow<Pair<String, ByteArray?>>, output: File) {
        val dir = output.absoluteFile.parentFile
        if (!dir.exists()) {
            dir.mkdirs()
        } else if (output.exists()) {
            output.delete()
        }

        ZipOutputStream(output.outputStream().buffered(1024 * 1024)).use { stream ->
            stream.setLevel(Deflater.BEST_COMPRESSION)
            @Suppress("BlockingMethodInNonBlockingContext")
            files.collect { (path, bytes) ->
                stream.putNextEntry(ZipEntry(path))
                if (bytes != null) {
                    stream.write(bytes)
                }
                stream.closeEntry()
            }
        }
    }
}