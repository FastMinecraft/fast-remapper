package dev.fastmc.jartools.remap

import org.objectweb.asm.commons.Remapper

open class AsmRemapper(private val classMapping: ClassMapping) : Remapper() {
    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        return classMapping.get(owner)?.methodMapping?.getNameTo(name, descriptor) ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return classMapping.get(owner)?.fieldMapping?.getNameTo(name) ?: name
    }

    override fun mapType(internalName: String): String {
        return classMapping.getNameTo(internalName) ?: internalName
    }
}