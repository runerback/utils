package com.runerback.screenrecorder.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecordingStatus {
    IDLE,
    PREPARING,
    RECORDING,
    STOPPING,
}

data class RecorderUiState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val lastOutputUri: String? = null,
    val errorMessage: String? = null,
    val isAudioCaptureActive: Boolean = false,
    val isToolboxVisible: Boolean = false,
)

object RecordingStateRepository {
    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()

    fun setPreparing() {
        _uiState.value = _uiState.value.copy(
            status = RecordingStatus.PREPARING,
            errorMessage = null,
            lastOutputUri = null,
            isAudioCaptureActive = false,
        )
    }

    fun setRecording(isAudioCaptureActive: Boolean) {
        _uiState.value = _uiState.value.copy(
            status = RecordingStatus.RECORDING,
            errorMessage = null,
            isAudioCaptureActive = isAudioCaptureActive,
            isToolboxVisible = true,
        )
    }

    fun setStopping() {
        _uiState.value = _uiState.value.copy(status = RecordingStatus.STOPPING)
    }

    fun setIdle(
        lastOutputUri: String? = _uiState.value.lastOutputUri,
        isToolboxVisible: Boolean = _uiState.value.isToolboxVisible,
    ) {
        _uiState.value = RecorderUiState(
            status = RecordingStatus.IDLE,
            lastOutputUri = lastOutputUri,
            isAudioCaptureActive = false,
            isToolboxVisible = isToolboxVisible,
        )
    }

    fun setError(
        message: String,
        lastOutputUri: String? = _uiState.value.lastOutputUri,
        isToolboxVisible: Boolean = _uiState.value.isToolboxVisible,
    ) {
        _uiState.value = RecorderUiState(
            status = RecordingStatus.IDLE,
            lastOutputUri = lastOutputUri,
            errorMessage = message,
            isAudioCaptureActive = false,
            isToolboxVisible = isToolboxVisible,
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun setToolboxVisible(isVisible: Boolean) {
        _uiState.value = _uiState.value.copy(
            isToolboxVisible = isVisible,
            errorMessage = if (isVisible) null else _uiState.value.errorMessage,
        )
    }
}
