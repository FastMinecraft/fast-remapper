package dev.luna5ama.jartools.util

import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

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

fun List<AnnotationNode>.containsAnnotation(descriptor: String): Boolean {
    return any { it.desc == descriptor }
}