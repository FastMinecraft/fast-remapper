package dev.fastmc.jartools.pipeline

interface Stage {
    suspend fun run(files: Map<String, JarEntry>): Map<String, JarEntry>
}

class SequenceStage(private vararg val tasks: Stage) : Stage {
    override suspend fun run(files: Map<String, JarEntry>): Map<String, JarEntry> {
        return tasks.fold(files) { prev, it ->
            it.run(prev)
        }
    }
}