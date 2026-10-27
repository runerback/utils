package com.runerback.imagehelper.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.runerback.imagehelper.model.FrameTransform
import com.runerback.imagehelper.model.GifFrame
import com.runerback.imagehelper.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GifMakerState(
    val gifWidth: Int? = null,
    val gifHeight: Int? = null,
    val frames: List<GifFrame> = emptyList(),
    val selectedFrameIndex: Int? = null,
    val editMode: Boolean = false
)

class GifMakerViewModel : ViewModel() {

    companion object {
        private const val TAG = "GifMakerViewModel"
    }

    private val _state = MutableStateFlow(GifMakerState())
    val state: StateFlow<GifMakerState> = _state.asStateFlow()

    init {
        LogManager.d(TAG, "init")
    }

    fun setSize(width: Int, height: Int) {
        try {
            _state.update { it.copy(gifWidth = width, gifHeight = height) }
        } catch (e: Exception) {
            LogManager.e(TAG, "setSize failed", e)
        }
    }

    fun addFrames(uris: List<Uri>) {
        try {
            if (uris.isEmpty()) return
            _state.update { state ->
                val newFrames = state.frames + uris.map { GifFrame(imageUri = it) }
                state.copy(
                    frames = newFrames,
                    selectedFrameIndex = state.selectedFrameIndex ?: state.frames.size
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "addFrames failed", e)
        }
    }

    fun selectFrame(index: Int) {
        try {
            _state.update { state ->
                if (index !in state.frames.indices) {
                    LogManager.w(TAG, "selectFrame: index $index out of bounds (${state.frames.size} frames)")
                    return@update state
                }
                state.copy(
                    selectedFrameIndex = index,
                    editMode = if (index == state.selectedFrameIndex) state.editMode else false
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "selectFrame failed", e)
        }
    }

    fun setEditMode(edit: Boolean) {
        _state.update { it.copy(editMode = edit && it.selectedFrameIndex != null) }
    }

    fun updateFrameTransform(index: Int, transform: FrameTransform) {
        try {
            _state.update { state ->
                if (index !in state.frames.indices) {
                    LogManager.w(TAG, "updateFrameTransform: index $index out of bounds")
                    return@update state
                }
                state.copy(
                    frames = state.frames.mapIndexed { i, frame ->
                        if (i == index) frame.copy(transform = transform) else frame
                    }
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "updateFrameTransform failed", e)
        }
    }

    fun resetFrameTransform(index: Int) {
        updateFrameTransform(index, FrameTransform())
    }
}
