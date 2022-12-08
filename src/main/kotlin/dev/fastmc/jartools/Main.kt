package dev.fastmc.jartools

import dev.fastmc.jartools.remap.MixinMappingProvider
import dev.fastmc.jartools.remap.SequenceMappingProvider
import dev.fastmc.jartools.remap.SubClassMappingProvider
import dev.fastmc.jartools.remap.Tsrg2MappingProvider
import dev.fastmc.jartools.util.JarUtils
import dev.fastmc.jartools.util.annotations
import dev.fastmc.jartools.util.containsAnnotation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.objectweb.asm.commons.Remapper
import java.io.File

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        //test()
    }

    fun test() {
        val input = File("TrollHack-0.0.6.jar")
//        val input = File("TrollHack-0.0.6-Release.jar")
        repeat(100) {
            val time = System.nanoTime()
            run(input)
            println("%.2f ms".format((System.nanoTime() - time) / 1_000_000.0))
        }
    }

    fun run(input: File) {
        runBlocking {
            withContext(Dispatchers.Default) {
                val output = File("${input.nameWithoutExtension}-Remapped.jar")
                val tasks = SequenceClassTask(
                    object : RemapTask() {
                        override suspend fun remapper(classEntry: Flow<ClassEntry>): Remapper {
                            return coroutineScope {
                                val inputClassNodes = async {
                                    JarUtils.readClassNodes(File("D:\\.gradle\\caches\\forge_gradle\\minecraft_user_repo\\net\\minecraftforge\\forge\\1.12.2-14.23.5.2860_mapped_stable_39-1.12\\forge-1.12.2-14.23.5.2860_mapped_stable_39-1.12-recomp.jar"))
                                }
                                val classNodesDeferred = async {
                                    classEntry.map { it.classNode }.toList()
                                }
                                val mixinClassesDeferred = async {
                                    classNodesDeferred.await().filter {
                                        it.annotations.containsAnnotation("Lorg/spongepowered/asm/mixin/Mixin;")
                                    }.toList().associateBy { it.name }
                                }
                                val mapping = SequenceMappingProvider(
                                    Tsrg2MappingProvider(File("E:\\CodeShit\\TrollHack\\build\\createMcpToSrg\\output.tsrg")),
                                    MixinMappingProvider(mixinClassesDeferred),
                                    SubClassMappingProvider(async {
                                        listOf(classNodesDeferred, inputClassNodes).awaitAll().flatten()
                                    })
                                ).get(classNodesDeferred)

                                val mixinClasses = mixinClassesDeferred.await()
                                MixinRemapper(mapping, mixinClasses)
                            }
                        }
                    }
                )

                JarTaskExecutors(input, output).execute(tasks)
            }
        }
    }

}