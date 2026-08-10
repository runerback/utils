package com.runerback.openposeeditor.skeleton

import kotlinx.serialization.Serializable
import org.joml.Quaternionf

@Serializable
class SkeletonPose(
    val keypointStates: List<KeypointStateData> = emptyList(),
)

@Serializable
class KeypointStateData(
    val id: Int,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val rotationW: Float = 1f,
    val enabled: Boolean = true,
) {
    constructor(state: KeypointState) : this(
        id = state.id,
        rotationX = state.localRotation.x,
        rotationY = state.localRotation.y,
        rotationZ = state.localRotation.z,
        rotationW = state.localRotation.w,
        enabled = state.enabled,
    )

    fun toKeypointState(): KeypointState = KeypointState(
        id = id,
        localRotation = Quaternionf(rotationX, rotationY, rotationZ, rotationW),
        enabled = enabled,
    )
}

fun Skeleton.capturePose(): SkeletonPose = SkeletonPose(
    keypointStates = keypoints.map { KeypointStateData(it.copyState()) },
)

fun Skeleton.applyPose(pose: SkeletonPose) {
    val stateMap = pose.keypointStates.associateBy { it.id }
    keypoints.forEach { kp ->
        stateMap[kp.id]?.toKeypointState()?.let { kp.applyState(it) }
    }
}
