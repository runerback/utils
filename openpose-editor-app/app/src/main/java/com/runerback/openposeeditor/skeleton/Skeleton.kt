package com.runerback.openposeeditor.skeleton

import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Quaternionf

interface Skeleton {
    val keypoints: List<Keypoint>
    val bones: List<Bone>

    fun keypointById(id: Int): Keypoint? = keypoints.find { it.id == id }

    fun computeWorldPositions(): Map<Int, Vector3f> {
        val result = mutableMapOf<Int, Vector3f>()
        val keypointMap = keypoints.associateBy { it.id }

        fun computeFor(id: Int): Vector3f {
            result[id]?.let { return it }
            val kp = keypointMap[id] ?: return Vector3f()
            val parentPosition = kp.parentId?.let { computeFor(it) } ?: Vector3f()
            val rotatedLocal = Vector3f(kp.restLocalPosition).rotate(kp.localRotation)
            val world = Vector3f(parentPosition).add(rotatedLocal)
            result[id] = world
            return world
        }

        keypoints.forEach { computeFor(it.id) }
        return result
    }

    fun boundingBox(ids: Collection<Int> = keypoints.map { it.id }): Pair<Vector3f, Vector3f> {
        val positions = computeWorldPositions()
        val min = Vector3f(Float.MAX_VALUE)
        val max = Vector3f(-Float.MAX_VALUE)
        ids.forEach { id ->
            val p = positions[id] ?: return@forEach
            min.setMin(p)
            max.setMax(p)
        }
        if (min.x == Float.MAX_VALUE) {
            min.set(0f)
            max.set(0f)
        }
        return min to max
    }

    fun boundingSphere(ids: Collection<Int> = keypoints.map { it.id }): Pair<Vector3f, Float> {
        val (min, max) = boundingBox(ids)
        val center = Vector3f(min).add(max).mul(0.5f)
        val radius = center.distance(max)
        return center to radius
    }
}

private fun Vector3f.setMin(other: Vector3f) {
    x = minOf(x, other.x)
    y = minOf(y, other.y)
    z = minOf(z, other.z)
}

private fun Vector3f.setMax(other: Vector3f) {
    x = maxOf(x, other.x)
    y = maxOf(y, other.y)
    z = maxOf(z, other.z)
}
