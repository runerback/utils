package com.runerback.openposeeditor.ui

import com.runerback.openposeeditor.skeleton.KeypointGroup
import com.runerback.openposeeditor.skeleton.KeypointStateData
import com.runerback.openposeeditor.skeleton.SkeletonPose
import com.runerback.openposeeditor.skeleton.applyPose
import com.runerback.openposeeditor.skeleton.capturePose
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
class ProjectState(
    val version: Int = 1,
    val pose: SkeletonPose,
    val viewportCamera: CameraStateData,
    val renderCamera: CameraStateData,
    val visibleGroups: List<String>,
)

@Serializable
class CameraStateData(
    val azimuth: Float,
    val elevation: Float,
    val distance: Float,
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
) {
    constructor(state: CameraState) : this(
        azimuth = state.azimuth,
        elevation = state.elevation,
        distance = state.distance,
        targetX = state.target.x,
        targetY = state.target.y,
        targetZ = state.target.z,
    )

    fun toCameraState(): CameraState = CameraState(
        azimuth = azimuth,
        elevation = elevation,
        distance = distance,
        target = Vector3f(targetX, targetY, targetZ),
    )
}

fun EditorViewModel.captureProjectState(): ProjectState {
    return ProjectState(
        pose = editorState.skeleton.capturePose(),
        viewportCamera = CameraStateData(editorState.viewportCameraState.value),
        renderCamera = CameraStateData(editorState.renderCameraState.value),
        visibleGroups = KeypointGroup.entries.filter { group ->
            editorState.groupVisibility[group] == true
        }.map { it.name },
    )
}

fun EditorViewModel.applyProjectState(state: ProjectState) {
    editorState.skeleton.applyPose(state.pose)
    editorState.viewportCameraState.value = state.viewportCamera.toCameraState()
    editorState.renderCameraState.value = state.renderCamera.toCameraState()

    val visible = state.visibleGroups.mapNotNull { name ->
        runCatching { KeypointGroup.valueOf(name) }.getOrNull()
    }.toSet()
    KeypointGroup.entries.forEach { group ->
        val enabled = group in visible
        editorState.skeleton.keypoints.filter { it.group == group }.forEach { it.enabled = enabled }
        editorState.setGroupVisible(group, enabled)
    }

    applyCameraState(editorState.viewportCameraState.value)
    updateRenderCamera()
}
