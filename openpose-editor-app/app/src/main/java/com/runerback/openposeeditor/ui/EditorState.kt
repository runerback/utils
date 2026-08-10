package com.runerback.openposeeditor.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.runerback.openposeeditor.skeleton.KeypointGroup
import com.runerback.openposeeditor.skeleton.ProceduralSkeleton
import com.runerback.openposeeditor.skeleton.Skeleton
import org.joml.Vector3f

object EditorState {
    val skeleton: Skeleton = ProceduralSkeleton()

    val viewportCameraState = mutableStateOf(CameraState(180f, 0f, 4f, Vector3f(0f, 1f, 0f)))
    val renderCameraState = mutableStateOf(CameraState(180f, 0f, 4f, Vector3f(0f, 1f, 0f)))
    val groupVisibility = mutableStateMapOf<KeypointGroup, Boolean>()

    val selectedMenuCategory = mutableStateOf<MenuCategory?>(null)
    val poseModeEnabled = mutableStateOf(false)
    val lockCameraView = mutableStateOf(false)
    val selectedJointId = mutableStateOf<Int?>(null)

    init {
        KeypointGroup.entries.forEach { group ->
            groupVisibility[group] = skeleton.keypoints.any { it.group == group && it.enabled }
        }
    }

    fun selectMenuCategory(category: MenuCategory?) {
        selectedMenuCategory.value = category
    }

    fun toggleMenuCategory(category: MenuCategory) {
        selectedMenuCategory.value = if (selectedMenuCategory.value == category) null else category
    }

    fun closeMenu() {
        selectedMenuCategory.value = null
    }

    fun setPoseModeEnabled(enabled: Boolean) {
        poseModeEnabled.value = enabled
    }

    fun setLockCameraView(locked: Boolean) {
        lockCameraView.value = locked
    }

    fun setGroupVisible(group: KeypointGroup, visible: Boolean) {
        groupVisibility[group] = visible
    }
}
