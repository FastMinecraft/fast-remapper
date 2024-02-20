import dev.fastmc.remapper.Shared
import dev.fastmc.remapper.mapping.*
import dev.fastmc.remapper.pipeline.*
import dev.fastmc.remapper.util.JarUtils
import dev.fastmc.remapper.util.McVersion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.Remapper
import java.io.File

fun main() {
//    return
//    val file = File("joined.tsrg")
//    val output = File("test.txt")
//    val mapping = ExternalMappingParser.TSRG.parse(file.bufferedReader())
//    output.bufferedWriter().use {
//        InternalMappingParser.write(it, mapping)
//    }
    Shared.init(
        File("cache"),
        File("D:\\.gradle", "caches/fast-remapper/maven")
    )

    val testN = 20
    repeat(20) {
        run(File("input.jar"))
    }

    val startTime = System.nanoTime()
    repeat(testN) {
        run(File("input.jar"))
    }
    val endTime = System.nanoTime()
    val avgMS = (endTime - startTime) / testN.toDouble() / 1_000_000.0
    println("%.2f ms".format(avgMS))
}

fun run(input: File) {
    runBlocking {
        withContext(Dispatchers.Default) {
            val output = File("${input.nameWithoutExtension}-remapped.jar")

            val minecraftClasses = async(Dispatchers.IO) {
                JarUtils.unpackFlow(File("forge-1.12.2-14.23.5.2860_mapped_stable_39-1.12-recomp.jar"))
                    .filter { it.second != null }
                    .filter { it.first.endsWith(".class") }
                    .map {
                        ClassEntry(
                            it.first,
                            it.second!!,
                            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
                        )
                    }
                    .toList()
            }

            val rawMapping = async {
                val srcMapping = MappingName.Mcp("stable", 39)
                val mcVersion = McVersion("1.12.2")
                MappingProvider.getOrCompute(mcVersion, srcMapping, MappingName.Searge) {
                    MappingProvider.Searge2Mcp.provide(mcVersion, srcMapping).reversed()
                }
            }

            val mapping = SequenceMappingPipeline(
                MappingProviderPipeline(rawMapping),
                MixinMappingPipeline(),
                SubclassMappingPipeline(minecraftClasses)
            )

            val tasks = SequenceStage(
                object : RemapStage() {
                    override suspend fun remapper(classEntries: Collection<ClassEntry>): Remapper {
                        return coroutineScope {
                            MixinRemapper(mapping.get(classEntries), classEntries)
                        }
                    }
                },
            )

            JarTaskExecutor(input, output).execute(tasks)
        }
    }
}