package dev.luna5ama.jartools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface ClassTask {
    suspend fun run(scope: CoroutineScope, classEntry: Flow<ClassEntry>): Flow<ClassEntry>
}

class SequenceClassTask(private vararg val tasks: ClassTask) : ClassTask {
    override suspend fun run(scope: CoroutineScope, classEntry: Flow<ClassEntry>): Flow<ClassEntry> {
        return tasks.fold(classEntry) { prev, it ->
            it.run(scope, prev)
        }
    }
}