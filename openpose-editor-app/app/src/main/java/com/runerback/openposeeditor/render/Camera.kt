package com.runerback.openposeeditor.render

import org.joml.Matrix4f
import org.joml.Vector3f

class Camera(
    var fovDegrees: Float = 45f,
    var near: Float = 0.1f,
    var far: Float = 100f,
) {
    val position = Vector3f(0f, 1.5f, 4f)
    val target = Vector3f(0f, 1f, 0f)
    val up = Vector3f(0f, 1f, 0f)

    private val viewMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()
    private val viewProjectionMatrix = Matrix4f()

    fun lookAt(eye: Vector3f, center: Vector3f, upVec: Vector3f = up) {
        position.set(eye)
        target.set(center)
        up.set(upVec)
    }

    fun setOrbit(azimuthDegrees: Float, elevationDegrees: Float, distance: Float, center: Vector3f = target) {
        val azimuth = Math.toRadians(azimuthDegrees.toDouble())
        val elevation = Math.toRadians(elevationDegrees.toDouble())
        val x = center.x + (distance * Math.cos(elevation) * Math.sin(azimuth)).toFloat()
        val y = center.y + (distance * Math.sin(elevation)).toFloat()
        val z = center.z + (distance * Math.cos(elevation) * Math.cos(azimuth)).toFloat()
        position.set(x, y, z)
        target.set(center)
    }

    fun getViewProjectionMatrix(width: Int, height: Int): Matrix4f {
        val aspect = width.toFloat() / height.toFloat()
        projectionMatrix.setPerspective(Math.toRadians(fovDegrees.toDouble()).toFloat(), aspect, near, far)
        viewMatrix.setLookAt(position, target, up)
        return projectionMatrix.mul(viewMatrix, viewProjectionMatrix)
    }

    fun frameBoundingSphere(center: Vector3f, radius: Float, distanceMultiplier: Float = 2.5f) {
        target.set(center)
        val distance = radius * distanceMultiplier / Math.tan(Math.toRadians(fovDegrees * 0.5)).toFloat()
        val direction = Vector3f(position).sub(target).normalize()
        position.set(target).add(direction.mul(distance))
    }

    fun project(world: Vector3f, width: Int, height: Int): Pair<Float, Float> {
        val vp = getViewProjectionMatrix(width, height)
        val clip = vp.transformProject(Vector3f(world))
        val x = (clip.x * 0.5f + 0.5f) * width
        val y = (1f - (clip.y * 0.5f + 0.5f)) * height
        return x to y
    }

    fun rayFromScreen(screenX: Float, screenY: Float, width: Int, height: Int): Ray {
        val vp = Matrix4f(getViewProjectionMatrix(width, height)).invert()
        val ndcX = screenX / width * 2f - 1f
        val ndcY = 1f - screenY / height * 2f
        val nearNdc = Vector3f(ndcX, ndcY, -1f)
        val farNdc = Vector3f(ndcX, ndcY, 1f)
        vp.transformProject(nearNdc)
        vp.transformProject(farNdc)
        val direction = Vector3f(farNdc).sub(nearNdc).normalize()
        return Ray(nearNdc, direction)
    }
}

data class Ray(val origin: Vector3f, val direction: Vector3f)
