package dev.fastmc.jartools

import dev.fastmc.jartools.util.split
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode

abstract class RemapTask : ClassTask {
    abstract suspend fun remapper(classEntry: Flow<ClassEntry>): Remapper

    @OptIn(FlowPreview::class)
    override suspend fun run(scope: CoroutineScope, classEntry: Flow<ClassEntry>): Flow<ClassEntry> {
        val (mainFlow, remapperFlow) = classEntry.split(scope)

        val remapper = remapper(remapperFlow)

        return mainFlow.map {
            scope.async {
                val result = ClassNode()
                val classRemapper = ClassRemapper(result, remapper)
                it.classNode.accept(classRemapper)
                it.update(result)
            }
        }.produceIn(scope).consumeAsFlow().map {
            it.await()
        }
    }
}