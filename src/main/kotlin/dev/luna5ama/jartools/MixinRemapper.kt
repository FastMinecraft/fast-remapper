package dev.luna5ama.jartools

import dev.luna5ama.jartools.remap.AsmRemapper
import dev.luna5ama.jartools.remap.Mapping
import dev.luna5ama.jartools.util.annotations
import dev.luna5ama.jartools.util.containsAnnotation
import org.objectweb.asm.tree.ClassNode

class MixinRemapper(
    mapping: Mapping,
    private val mixinClasses: Map<String, ClassNode>
) : AsmRemapper(mapping) {
    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        val classNode =
            mixinClasses[owner] ?: return super.mapFieldName(owner, name, descriptor)

        var newName = ""
        classNode.fields.find { it.name == name && it.desc == descriptor }
            ?.let { fieldNode ->
                if (!fieldNode.annotations.containsAnnotation("Lorg/spongepowered/asm/mixin/Shadow;")) {
                    newName = name
                }
            }
        if (newName.isEmpty()) {
            newName = super.mapFieldName(owner, name, descriptor)
        }
        return newName
    }
}