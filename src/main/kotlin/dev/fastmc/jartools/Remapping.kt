package dev.fastmc.jartools

import dev.fastmc.jartools.mapping.ClassMapping
import dev.fastmc.jartools.mapping.get
import dev.fastmc.jartools.mapping.getNameTo
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