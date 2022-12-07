package dev.fastmc.jartools

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

data class ClassEntry(val fileName: String, val classBytes: ByteArray, val parseOptions: Int) {
    private val classNode0 by lazy { ClassNode().apply { classReader.accept(this, parseOptions) } }

    val classReader by lazy { ClassReader(classBytes) }
    val classNode by lazy { ClassNode().apply { classNode0.accept(this) } }
    val className get() = classNode0.name

    fun update(newBytes: ByteArray): ClassEntry {
        return copy(
            classBytes = newBytes,
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
        if (!classBytes.contentEquals(other.classBytes)) return false
        if (parseOptions != other.parseOptions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + classBytes.contentHashCode()
        result = 31 * result + parseOptions
        return result
    }
}