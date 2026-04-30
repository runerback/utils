package com.runerback.screenrecorder.data

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val recordingElapsedMillis: Long = 0L,
)

object RecordingStateRepository {
    private const val RECORDING_TIMER_INTERVAL_MILLIS = 200L

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()
    private var recordingTimerJob: Job? = null
    private var recordingStartedAtElapsedRealtime = 0L

    fun setPreparing() {
        resetRecordingTimer()
        _uiState.update { state ->
            state.copy(
                status = RecordingStatus.PREPARING,
                errorMessage = null,
                lastOutputUri = null,
                isAudioCaptureActive = false,
                recordingElapsedMillis = 0L,
            )
        }
    }

    fun setRecording(isAudioCaptureActive: Boolean) {
        recordingStartedAtElapsedRealtime = SystemClock.elapsedRealtime()
        _uiState.update { state ->
            state.copy(
                status = RecordingStatus.RECORDING,
                errorMessage = null,
                isAudioCaptureActive = isAudioCaptureActive,
                isToolboxVisible = true,
                recordingElapsedMillis = 0L,
            )
        }
        startRecordingTimer()
    }

    fun setStopping() {
        val elapsedMillis = currentRecordingElapsedMillis()
        cancelRecordingTimer()
        _uiState.update { state ->
            state.copy(
                status = RecordingStatus.STOPPING,
                recordingElapsedMillis = elapsedMillis,
            )
        }
    }

    fun setIdle(
        lastOutputUri: String? = _uiState.value.lastOutputUri,
        isToolboxVisible: Boolean = _uiState.value.isToolboxVisible,
    ) {
        resetRecordingTimer()
        _uiState.value = RecorderUiState(
            status = RecordingStatus.IDLE,
            lastOutputUri = lastOutputUri,
            isAudioCaptureActive = false,
            isToolboxVisible = isToolboxVisible,
            recordingElapsedMillis = 0L,
        )
    }

    fun setError(
        message: String,
        lastOutputUri: String? = _uiState.value.lastOutputUri,
        isToolboxVisible: Boolean = _uiState.value.isToolboxVisible,
    ) {
        resetRecordingTimer()
        _uiState.value = RecorderUiState(
            status = RecordingStatus.IDLE,
            lastOutputUri = lastOutputUri,
            errorMessage = message,
            isAudioCaptureActive = false,
            isToolboxVisible = isToolboxVisible,
            recordingElapsedMillis = 0L,
        )
    }

    fun clearError() {
        _uiState.update { state -> state.copy(errorMessage = null) }
    }

    fun setToolboxVisible(isVisible: Boolean) {
        _uiState.update { state ->
            state.copy(isToolboxVisible = isVisible)
        }
    }

    private fun startRecordingTimer() {
        cancelRecordingTimer()
        recordingTimerJob = repositoryScope.launch {
            while (isActive) {
                val elapsedMillis = currentRecordingElapsedMillis()
                _uiState.update { state ->
                    if (state.status == RecordingStatus.RECORDING) {
                        state.copy(recordingElapsedMillis = elapsedMillis)
                    } else {
                        state
                    }
                }
                delay(RECORDING_TIMER_INTERVAL_MILLIS)
            }
        }
    }

    private fun cancelRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    private fun resetRecordingTimer() {
        cancelRecordingTimer()
        recordingStartedAtElapsedRealtime = 0L
    }

    private fun currentRecordingElapsedMillis(): Long {
        if (recordingStartedAtElapsedRealtime == 0L) {
            return 0L
        }
        return (SystemClock.elapsedRealtime() - recordingStartedAtElapsedRealtime).coerceAtLeast(0L)
    }
}
