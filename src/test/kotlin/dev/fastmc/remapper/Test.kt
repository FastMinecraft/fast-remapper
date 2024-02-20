package dev.fastmc.remapper

import dev.fastmc.remapper.mapping.InternalMappingParser
import dev.fastmc.remapper.mapping.MappingName
import dev.fastmc.remapper.mapping.MappingProvider
import dev.fastmc.remapper.mapping.reversed
import dev.fastmc.remapper.util.McVersion
import kotlinx.coroutines.runBlocking
import java.io.File

private lateinit var lines: List<String>

fun test() {
    runBlocking {
    }
}

fun main() {
    lines = File("E:\\.gradle\\caches\\fast-remapper\\mappings\\1.20.2-intermediary-yarn.4.mapping").readLines()

    repeat(100) {
        test()
    }

    val testN = 100
    val startTime = System.nanoTime()
    repeat(testN) {
        test()
    }
    val endTime = System.nanoTime()
    val avgTime = (endTime - startTime) / testN.toDouble()
    val avgMs = avgTime / 1_000_000.0
    println("%.2f ms".format(avgMs))
}