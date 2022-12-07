package dev.luna5ama.jartools.util

import org.objectweb.asm.tree.ClassNode

class SubclassInfo private constructor(val classNode: ClassNode) {
    var depth = Int.MIN_VALUE
    val subclasses = mutableListOf<SubclassInfo>()

    override fun toString(): String {
        return "${classNode.name}(depth=$depth, subclasses=${subclasses.joinToString {it.classNode.name}})"
    }

    companion object {
        operator fun invoke(classNodes: Collection<ClassNode>): List<SubclassInfo> {
            val className2ClassNode = classNodes.associateBy { it.name }
            val subclassInfo = classNodes.associateWith { SubclassInfo(it) }

            subclassInfo.values.forEach {
                val superName = it.classNode.superName
                if (superName != null && superName != "java/lang/Object") {
                    val superClassNode = className2ClassNode[superName]
                    if (superClassNode != null) {
                        subclassInfo[superClassNode]!!.subclasses.add(it)
                    }
                }
                val interfaces = it.classNode.interfaces
                if (interfaces != null && interfaces.isNotEmpty()) {
                    interfaces.forEach { interfaceName ->
                        val interfaceClassNode = className2ClassNode[interfaceName]
                        if (interfaceClassNode != null) {
                            subclassInfo[interfaceClassNode]!!.subclasses.add(it)
                        }
                    }
                }
            }
            subclassInfo.forEach { (className, info) ->
                val superName = className.superName
                val interfaces = className.interfaces
                if ((superName == null || superName == "java/lang/Object")
                    && (interfaces == null || interfaces.isEmpty())
                ) {
                    info.depth = 0
                }
            }

            var updated = true
            while (updated) {
                updated = false
                subclassInfo.values.forEach { currentClass ->
                    currentClass.subclasses.forEach { subclass ->
                        val prevDepth = subclass.depth
                        val newDepth = currentClass.depth + 1
                        if (newDepth > prevDepth) {
                            subclass.depth = newDepth
                            updated = true
                        }
                    }
                }
            }

            return subclassInfo.values.sortedBy {
                it.depth
            }
        }
    }
}