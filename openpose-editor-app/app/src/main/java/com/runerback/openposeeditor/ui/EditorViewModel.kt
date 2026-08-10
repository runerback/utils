package com.runerback.openposeeditor.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.openposeeditor.render.Camera
import com.runerback.openposeeditor.skeleton.KeypointGroup
import com.runerback.openposeeditor.skeleton.ProceduralSkeleton
import com.runerback.openposeeditor.skeleton.Skeleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.tan

class EditorViewModel : ViewModel() {

    val editorState = EditorState

    val skeleton: Skeleton
        get() = editorState.skeleton

    val viewportCamera = Camera(fovDegrees = 45f)

    val renderCamera = Camera(fovDegrees = 45f)

    // Viewport camera orbit state (interactive workzone)
    var viewportAzimuth = mutableFloatStateOf(45f)
    var viewportElevation = mutableFloatStateOf(20f)
    var viewportDistance = mutableFloatStateOf(4f)

    val selectedGroup = mutableStateOf<KeypointGroup?>(null)

    var savedCameraState = mutableStateOf<CameraState?>(null)
        private set

    private var animationJob: Job? = null

    init {
        KeypointGroup.entries.forEach { group ->
            val visible = group == KeypointGroup.BODY
            editorState.setGroupVisible(group, visible)
            skeleton.keypoints.filter { it.group == group }.forEach { it.enabled = visible }
        }
        frameGroup(null, animate = false)
        updateRenderCamera()
    }

    fun selectMenuCategory(category: MenuCategory?) {
        editorState.selectMenuCategory(category)
    }

    fun toggleMenuCategory(category: MenuCategory) {
        editorState.toggleMenuCategory(category)
    }

    fun closeMenu() {
        editorState.closeMenu()
    }

    fun setPoseModeEnabled(enabled: Boolean) {
        editorState.setPoseModeEnabled(enabled)
        if (!enabled) {
            editorState.selectedJointId.value = null
        }
    }

    fun selectJoint(jointId: Int?) {
        editorState.selectedJointId.value = jointId
    }

    fun moveSelectedJointByScreenDelta(dx: Float, dy: Float, width: Int, height: Int) {
        val id = editorState.selectedJointId.value ?: return
        val kp = skeleton.keypointById(id) ?: return
        val parentId = kp.parentId ?: return

        val positions = skeleton.computeWorldPositions()
        val currentPos = positions[id] ?: return
        val parentPos = positions[parentId] ?: return
        val radius = kp.restLocalPosition.length()
        if (radius < 0.0001f) return

        val viewDir = Vector3f(viewportCamera.target).sub(viewportCamera.position).normalize()
        val (screenX, screenY) = viewportCamera.project(currentPos, width, height)
        val ray = viewportCamera.rayFromScreen(screenX + dx, screenY + dy, width, height)
        val hit = intersectRayPlane(ray.origin, ray.direction, currentPos, viewDir) ?: return

        val desiredDir = Vector3f(hit).sub(parentPos).normalize()
        if (!desiredDir.isFinite) return
        val rotation = Quaternionf().rotationTo(Vector3f(kp.restLocalPosition).normalize(), desiredDir)
        kp.localRotation.set(rotation)
    }

    private fun intersectRayPlane(
        origin: Vector3f,
        direction: Vector3f,
        planePoint: Vector3f,
        planeNormal: Vector3f,
    ): Vector3f? {
        val denom = direction.dot(planeNormal)
        if (kotlin.math.abs(denom) < 1e-6f) return null
        val t = Vector3f(planePoint).sub(origin).dot(planeNormal) / denom
        if (t < 0f) return null
        return Vector3f(origin).add(direction.x * t, direction.y * t, direction.z * t)
    }

    fun updateViewportCamera() {
        viewportCamera.setOrbit(
            viewportAzimuth.floatValue,
            viewportElevation.floatValue,
            viewportDistance.floatValue,
            Vector3f(viewportCamera.target),
        )
        editorState.viewportCameraState.value = captureCameraState()
    }

    fun updateRenderCamera() {
        val state = editorState.renderCameraState.value
        renderCamera.setOrbit(
            state.azimuth,
            state.elevation,
            state.distance,
            Vector3f(state.target),
        )
    }

    fun setLockCameraView(locked: Boolean) {
        editorState.setLockCameraView(locked)
        if (locked) {
            syncRenderCameraToViewport()
        }
    }

    fun syncRenderCameraToViewport() {
        editorState.renderCameraState.value = captureCameraState()
        updateRenderCamera()
    }

    fun toggleGroup(group: KeypointGroup) {
        val enabled = !(editorState.groupVisibility[group] ?: true)
        editorState.setGroupVisible(group, enabled)
        skeleton.keypoints.filter { it.group == group }.forEach { it.enabled = enabled }
    }

    fun frameGroup(group: KeypointGroup?, animate: Boolean = true) {
        val ids = if (group == null) {
            skeleton.keypoints.map { it.id }
        } else {
            skeleton.keypoints.filter { it.group == group && it.enabled }.map { it.id }
        }
        if (ids.isEmpty()) return

        val (center, radius) = skeleton.boundingSphere(ids)
        val targetState = computeFrameState(center, radius)

        if (animate) {
            savedCameraState.value = captureCameraState()
            animateTo(targetState)
        } else {
            applyCameraState(targetState)
            if (editorState.lockCameraView.value) {
                syncRenderCameraToViewport()
            }
        }
    }

    fun returnToPreviousCamera() {
        val state = savedCameraState.value ?: return
        savedCameraState.value = null
        animateTo(state)
    }

    private fun computeFrameState(center: Vector3f, radius: Float): CameraState {
        val currentDir = Vector3f(viewportCamera.position).sub(viewportCamera.target).normalize()
        val distance = (radius * FRAME_DISTANCE_MULTIPLIER / tan(Math.toRadians(viewportCamera.fovDegrees * 0.5).toFloat())).toFloat()
        val position = Vector3f(center).add(currentDir.mul(distance))
        return cameraStateFromPosition(position, center)
    }

    private fun cameraStateFromPosition(position: Vector3f, target: Vector3f): CameraState {
        val dir = Vector3f(position).sub(target)
        val distance = dir.length()
        val azimuth = Math.toDegrees(atan2(dir.x.toDouble(), dir.z.toDouble())).toFloat()
        val elevation = Math.toDegrees(asin((dir.y / distance).toDouble())).toFloat()
        return CameraState(azimuth, elevation, distance, Vector3f(target))
    }

    fun captureCameraState(): CameraState = CameraState(
        azimuth = viewportAzimuth.floatValue,
        elevation = viewportElevation.floatValue,
        distance = viewportDistance.floatValue,
        target = Vector3f(viewportCamera.target),
    )

    internal fun applyCameraState(state: CameraState) {
        viewportAzimuth.floatValue = state.azimuth
        viewportElevation.floatValue = state.elevation
        viewportDistance.floatValue = state.distance
        viewportCamera.target.set(state.target)
        updateViewportCamera()
        editorState.viewportCameraState.value = state
    }

    fun resetViewportCamera() {
        val default = CameraState(180f, 0f, 4f, Vector3f(0f, 1f, 0f))
        applyCameraState(default)
    }

    private fun animateTo(target: CameraState, durationMs: Long = 600) {
        animationJob?.cancel()
        val start = captureCameraState()
        animationJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val t = (elapsed / durationMs.toFloat()).coerceIn(0f, 1f)
                val eased = easeInOutCubic(t)

                viewportAzimuth.floatValue = lerpAngle(start.azimuth, target.azimuth, eased)
                viewportElevation.floatValue = lerp(start.elevation, target.elevation, eased)
                viewportDistance.floatValue = lerp(start.distance, target.distance, eased)
                viewportCamera.target.set(lerpVector(start.target, target.target, eased))
                updateViewportCamera()

                if (editorState.lockCameraView.value) {
                    syncRenderCameraToViewport()
                }

                if (t >= 1f) break
                delay(16)
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = b - a
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return a + diff * t
    }

    private fun lerpVector(a: Vector3f, b: Vector3f, t: Float): Vector3f {
        return Vector3f(
            lerp(a.x, b.x, t),
            lerp(a.y, b.y, t),
            lerp(a.z, b.z, t),
        )
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4f * t * t * t
        } else {
            1f - (-2f * t + 2f).pow(3) / 2f
        }
    }

    companion object {
        private const val FRAME_DISTANCE_MULTIPLIER = 2.5f
    }
}

data class CameraState(
    val azimuth: Float,
    val elevation: Float,
    val distance: Float,
    val target: Vector3f,
)
