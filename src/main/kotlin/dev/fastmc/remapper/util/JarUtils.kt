package dev.fastmc.remapper.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object JarUtils {

    fun unpackFlow(scope: CoroutineScope, input: CopyActionProcessingStream): Flow<Pair<String, ByteArray?>> {
        val channel = Channel<Pair<String, ByteArray?>>(Channel.UNLIMITED)
        scope.launch {
            input.process {
                if (it.isDirectory) {
                    channel.trySend(it.relativePath.pathString + '/' to null)
                } else {
                    val outputStream = ByteArrayOutputStream()
                    it.copyTo(outputStream)
                    channel.trySend(it.relativePath.pathString to outputStream.toByteArray())
                }
            }
            channel.close()
        }
        return channel.consumeAsFlow()
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