package dev.luna5ama.jartools

import dev.luna5ama.jartools.util.JarUtils
import dev.luna5ama.jartools.util.split
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File

interface TaskExecutor {
    suspend fun execute(task: ClassTask)
}

class JarTaskExecutors(private val input: File, private val output: File) : TaskExecutor {
    init {
        require(input.exists()) { "Input file does not exist" }
        require(input.isFile) { "Input is not a file" }
        require(input.extension == "jar") { "Input is not a jar file" }
    }

    override suspend fun execute(task: ClassTask) {
        coroutineScope {
            val (cachedFileFlow, classEntryFlow) = JarUtils.unpackFlow(input).split(this)

            val classEntry = classEntryFlow
                .filter {
                    it.second != null && it.first.endsWith(".class")
                }.map {
                    ClassEntry(it.first, it.second!!, 0)
                }

            val result = task.run(this, classEntry)
            val modified = hashSetOf<String>()

            val channel = Channel<Pair<String, ByteArray?>>()

            launch {
                result.collect {
//                    println(it.fileName)
                    modified.add(it.fileName)
                    channel.send(it.fileName to it.classBytes)
                }
                cachedFileFlow.collect {
                    if (!modified.contains(it.first)) {
                        channel.send(it)
                    }
                }
                channel.close()
            }

            withContext(Dispatchers.IO) {
                JarUtils.repackFlow(channel.consumeAsFlow(), output)
            }
        }
    }
}