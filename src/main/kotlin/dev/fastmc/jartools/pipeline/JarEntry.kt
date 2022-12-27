@file:Suppress("NOTHING_TO_INLINE")

package dev.fastmc.jartools.pipeline

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

open class JarEntry(val fileName: String)

open class JarFileEntry(fileName: String, val bytes: ByteArray) : JarEntry(fileName)

class ClassEntry(fileName: String, bytes: ByteArray, val parseOptions: Int = 0) : JarFileEntry(fileName, bytes) {
    private val classNode0 by lazy { ClassNode().apply { classReader.accept(this, parseOptions) } }

    val classReader by lazy { ClassReader(bytes) }
    val classNode by lazy { ClassNode().apply { classNode0.accept(this) } }
    val className get() = classNode0.name

    fun update(newBytes: ByteArray): ClassEntry {
        return ClassEntry(
            fileName,
            newBytes,
            parseOptions
        )
    }

    fun update(newNode: ClassNode): ClassEntry {
        val classWriter = ClassWriter(classReader, 0)
        newNode.accept(classWriter)
        return update(classWriter.toByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClassEntry) return false

        if (fileName != other.fileName) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (parseOptions != other.parseOptions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + parseOptions
        return result
    }
}

inline fun <T : JarEntry> MutableMap<String, T>.put(entry: T) {
    this[entry.fileName] = entry
}

inline fun <reified T: JarEntry> Map<String, JarEntry>.filterValueType(): Object2ObjectOpenHashMap<String, T> {
    val result = Object2ObjectOpenHashMap<String, T>()
    this.values.forEach {
        if (it is T) {
            result[it.fileName] = it
        }
    }
    return result
}