package com.runerback.openposeeditor.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object PrimitiveMeshes {

    fun createSphere(radius: Float, stacks: Int = 12, slices: Int = 16): MeshData {
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        for (stack in 0..stacks) {
            val phi = PI * stack / stacks
            for (slice in 0..slices) {
                val theta = 2.0 * PI * slice / slices
                val x = (sin(phi) * cos(theta)).toFloat() * radius
                val y = (cos(phi)).toFloat() * radius
                val z = (sin(phi) * sin(theta)).toFloat() * radius
                vertices.addAll(listOf(x, y, z))
            }
        }

        for (stack in 0 until stacks) {
            for (slice in 0 until slices) {
                val i0 = stack * (slices + 1) + slice
                val i1 = i0 + 1
                val i2 = i0 + (slices + 1)
                val i3 = i2 + 1
                indices.addAll(listOf(i0, i2, i1, i1, i2, i3))
            }
        }

        return MeshData(vertices.toFloatArray(), indices.toIntArray())
    }

    fun createCylinder(radius: Float, length: Float, segments: Int = 12): MeshData {
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()
        val halfLength = length / 2f

        for (i in 0..1) {
            val y = if (i == 0) -halfLength else halfLength
            for (seg in 0..segments) {
                val angle = 2.0 * PI * seg / segments
                val x = (cos(angle)).toFloat() * radius
                val z = (sin(angle)).toFloat() * radius
                vertices.addAll(listOf(x, y, z))
            }
        }

        for (seg in 0 until segments) {
            val b0 = seg
            val b1 = seg + 1
            val t0 = b0 + segments + 1
            val t1 = b1 + segments + 1
            indices.addAll(listOf(b0, t0, b1, b1, t0, t1))
        }

        return MeshData(vertices.toFloatArray(), indices.toIntArray())
    }

    fun createCameraFrustum(
        near: Float = 0.05f,
        far: Float = 0.25f,
        nearHalfSize: Float = 0.03f,
        farHalfSize: Float = 0.12f,
    ): MeshData {
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Near rectangle (local -Z)
        vertices.addAll(listOf(-nearHalfSize, -nearHalfSize, -near))
        vertices.addAll(listOf(nearHalfSize, -nearHalfSize, -near))
        vertices.addAll(listOf(nearHalfSize, nearHalfSize, -near))
        vertices.addAll(listOf(-nearHalfSize, nearHalfSize, -near))

        // Far rectangle (local -Z)
        vertices.addAll(listOf(-farHalfSize, -farHalfSize, -far))
        vertices.addAll(listOf(farHalfSize, -farHalfSize, -far))
        vertices.addAll(listOf(farHalfSize, farHalfSize, -far))
        vertices.addAll(listOf(-farHalfSize, farHalfSize, -far))

        // Near rectangle edges
        indices.addAll(listOf(0, 1, 1, 2, 2, 3, 3, 0))
        // Far rectangle edges
        indices.addAll(listOf(4, 5, 5, 6, 6, 7, 7, 4))
        // Connecting edges
        indices.addAll(listOf(0, 4, 1, 5, 2, 6, 3, 7))

        return MeshData(vertices.toFloatArray(), indices.toIntArray())
    }

    data class MeshData(val vertices: FloatArray, val indices: IntArray)
}
