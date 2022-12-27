package dev.fastmc.remapper.pipeline

import dev.fastmc.remapper.util.JarUtils
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

interface TaskExecutor {
    suspend fun execute(task: Stage)
}

class JarTaskExecutor(private val inputFile: File, private val outputFile: File) : TaskExecutor {
    init {
        require(inputFile.exists()) { "Input file does not exist" }
        require(inputFile.isFile) { "Input is not a file" }
        require(inputFile.extension == "jar") { "Input is not a jar file" }
    }

    override suspend fun execute(task: Stage) {
        coroutineScope {
            val cachedFileFlow = JarUtils.unpackFlow(inputFile)

            val jarEntryFlow = cachedFileFlow.map {
                when {
                    it.second == null -> JarEntry(it.first)
                    it.first.endsWith(".class") -> ClassEntry(it.first, it.second!!)
                    else -> JarFileEntry(it.first, it.second!!)
                }
            }

            val files = Object2ObjectOpenHashMap<String, JarEntry>()
            jarEntryFlow.collect {
                files[it.fileName] = it
            }
            val result = task.run(files)

            withContext(Dispatchers.IO) {
                val output = flow {
                    result.values.forEach {
                        emit(it.fileName to (it as? JarFileEntry)?.bytes)
                    }
                }
                JarUtils.repackFlow(output, outputFile)
            }
        }
    }
}