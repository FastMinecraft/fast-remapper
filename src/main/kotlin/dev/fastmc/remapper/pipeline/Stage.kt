package dev.fastmc.remapper.pipeline

interface Stage {
    suspend fun run(files: Map<String, JarEntry>): Map<String, JarEntry>
}

class SequenceStage(private val tasks: List<Stage>) : Stage {
    constructor(vararg tasks: Stage) : this(tasks.toList())

    override suspend fun run(files: Map<String, JarEntry>): Map<String, JarEntry> {
        return tasks.fold(files) { prev, it ->
            it.run(prev)
        }
    }
}