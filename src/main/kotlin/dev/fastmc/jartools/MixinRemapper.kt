package dev.fastmc.jartools

import dev.fastmc.jartools.remap.AsmRemapper
import dev.fastmc.jartools.remap.ClassMapping
import dev.fastmc.jartools.util.annotations
import dev.fastmc.jartools.util.containsAnnotation
import org.objectweb.asm.tree.ClassNode

class MixinRemapper(
    classMapping: ClassMapping,
    private val mixinClasses: Map<String, ClassNode>
) : AsmRemapper(classMapping) {
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