package com.runerback.imagehelper.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.imagehelper.model.FrameTransform
import com.runerback.imagehelper.model.GifFrame
import com.runerback.imagehelper.util.GifExporter
import com.runerback.imagehelper.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GifMakerState(
    val gifWidth: Int? = null,
    val gifHeight: Int? = null,
    val frames: List<GifFrame> = emptyList(),
    val selectedFrameIndex: Int? = null,
    val editMode: Boolean = false,
    /** scale the workzone displays the canvas at; converts screen-px offsets to gif px */
    val canvasFitScale: Float = 1f,
    val exporting: Boolean = false,
    val exportProgress: Float = 0f,
    /** one-shot user-visible message; cleared via consumeExportMessage */
    val exportMessage: String? = null
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

    fun setCanvasFitScale(scale: Float) {
        if (scale <= 0f) return
        _state.update { state ->
            if (state.canvasFitScale == scale) state else state.copy(canvasFitScale = scale)
        }
    }

    fun exportGif(context: Context, output: Uri, background: Int) {
        val snapshot = _state.value
        val width = snapshot.gifWidth
        val height = snapshot.gifHeight
        if (width == null || height == null || snapshot.frames.isEmpty() || snapshot.exporting) return
        _state.update { it.copy(exporting = true, exportProgress = 0f, exportMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = GifExporter.export(
                context = context,
                output = output,
                frames = snapshot.frames,
                width = width,
                height = height,
                background = background,
                fitScale = snapshot.canvasFitScale,
                onProgress = { progress ->
                    _state.update { it.copy(exportProgress = progress) }
                }
            )
            _state.update {
                it.copy(
                    exporting = false,
                    exportMessage = result.fold(
                        onSuccess = { "GIF saved" },
                        onFailure = { e -> "Export failed: ${e.message}" }
                    )
                )
            }
        }
    }

    fun consumeExportMessage() {
        _state.update { it.copy(exportMessage = null) }
    }
}
