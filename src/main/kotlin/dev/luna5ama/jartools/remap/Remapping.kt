package dev.luna5ama.jartools.remap

import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode

open class AsmRemapper(private val mapping: Mapping) : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        return mapping[owner]?.getMethodNameTo(name, descriptor) ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return mapping[owner]?.getFieldNameTo(name) ?: name
    }

    override fun mapType(internalName: String): String {
        return mapping[internalName]?.nameTo ?: internalName
    }
}