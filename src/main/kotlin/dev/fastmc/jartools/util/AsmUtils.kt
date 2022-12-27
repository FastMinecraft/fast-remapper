package dev.fastmc.jartools.util

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

val ClassNode.annotations: List<AnnotationNode>
    get() {
        val invisibleAnnotations = invisibleAnnotations
        val visibleAnnotations = visibleAnnotations
        val hasInvisibleAnnotations = invisibleAnnotations != null && invisibleAnnotations.isNotEmpty()
        val hasVisibleAnnotations = visibleAnnotations != null && visibleAnnotations.isNotEmpty()
        return when {
            hasInvisibleAnnotations && hasVisibleAnnotations -> {
                invisibleAnnotations + visibleAnnotations
            }
            hasInvisibleAnnotations -> {
                invisibleAnnotations
            }
            hasVisibleAnnotations -> {
                visibleAnnotations
            }
            else -> {
                emptyList()
            }
        }
    }

val FieldNode.annotations: List<AnnotationNode>
    get() {
        val invisibleAnnotations = invisibleAnnotations
        val visibleAnnotations = visibleAnnotations
        val hasInvisibleAnnotations = invisibleAnnotations != null && invisibleAnnotations.isNotEmpty()
        val hasVisibleAnnotations = visibleAnnotations != null && visibleAnnotations.isNotEmpty()
        return when {
            hasInvisibleAnnotations && hasVisibleAnnotations -> {
                invisibleAnnotations + visibleAnnotations
            }
            hasInvisibleAnnotations -> {
                invisibleAnnotations
            }
            hasVisibleAnnotations -> {
                visibleAnnotations
            }
            else -> {
                emptyList()
            }
        }
    }

val MethodNode.annotations: List<AnnotationNode>
    get() {
        val invisibleAnnotations = invisibleAnnotations
        val visibleAnnotations = visibleAnnotations
        val hasInvisibleAnnotations = invisibleAnnotations != null && invisibleAnnotations.isNotEmpty()
        val hasVisibleAnnotations = visibleAnnotations != null && visibleAnnotations.isNotEmpty()
        return when {
            hasInvisibleAnnotations && hasVisibleAnnotations -> {
                invisibleAnnotations + visibleAnnotations
            }
            hasInvisibleAnnotations -> {
                invisibleAnnotations
            }
            hasVisibleAnnotations -> {
                visibleAnnotations
            }
            else -> {
                emptyList()
            }
        }
    }

fun List<AnnotationNode>.containsAnnotation(descriptor: String): Boolean {
    return any { it.desc == descriptor }
}

fun List<AnnotationNode>.containsAnyAnnotation(vararg descriptors: String): Boolean {
    val set = ObjectOpenHashSet(descriptors)
    return any { set.contains(it.desc) }
}

fun List<AnnotationNode>.findAnnotation(descriptor: String): AnnotationNode? {
    return find { it.desc == descriptor }
}

fun List<AnnotationNode>.findAnyAnnotation(vararg descriptors: String): AnnotationNode? {
    val set = ObjectOpenHashSet(descriptors)
    return find { set.contains(it.desc) }
}

fun ClassNode.findMixinAnnotation(): AnnotationNode? {
    return annotations.findAnnotation("Lorg/spongepowered/asm/mixin/Mixin;")
}