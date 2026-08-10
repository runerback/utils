package com.runerback.openposeeditor.skeleton

import org.joml.Quaternionf
import org.joml.Vector3f

class Keypoint(
    val id: Int,
    val name: String,
    val group: KeypointGroup,
    val parentId: Int? = null,
    val color: Int,
    var enabled: Boolean = true,
    val restLocalPosition: Vector3f = Vector3f(),
) {
    val localRotation: Quaternionf = Quaternionf()

    fun copyState(): KeypointState = KeypointState(
        id = id,
        localRotation = Quaternionf(localRotation),
        enabled = enabled,
    )

    fun applyState(state: KeypointState) {
        localRotation.set(state.localRotation)
        enabled = state.enabled
    }
}

class KeypointState(
    val id: Int,
    val localRotation: Quaternionf = Quaternionf(),
    val enabled: Boolean = true,
)
