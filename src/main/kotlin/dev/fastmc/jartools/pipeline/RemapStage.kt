package dev.fastmc.jartools.pipeline

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode

abstract class RemapStage : Stage {
    abstract suspend fun remapper(classEntries: Collection<ClassEntry>): Remapper

    override suspend fun run(files: Map<String, JarEntry>): Map<String, JarEntry> {
        return coroutineScope {
            val result = Object2ObjectOpenHashMap(files)
            val classEntries = result.filterValueType<ClassEntry>()
            val remapper = remapper(classEntries.values)

            val channel = Channel<JarEntry>(Runtime.getRuntime().availableProcessors())
            classEntries.values.forEach {
                launch {
                    val newNode = ClassNode()
                    val classRemapper = ClassRemapper(newNode, remapper)
                    it.classNode.accept(classRemapper)
                    channel.send(it.update(newNode))
                }
            }
            for (e in channel) {
                result.put(e)
            }

            result
        }
    }
}