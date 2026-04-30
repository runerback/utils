package com.runerback.screenrecorder.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.screenrecorder.data.FrameRatePreset
import com.runerback.screenrecorder.data.RecorderSettings
import com.runerback.screenrecorder.data.RecorderSettingsRepository
import com.runerback.screenrecorder.data.RecorderUiState
import com.runerback.screenrecorder.data.RecordingStateRepository
import com.runerback.screenrecorder.data.RecordingStore
import com.runerback.screenrecorder.data.ResolutionPreset
import com.runerback.screenrecorder.service.FloatingControlsCommands
import com.runerback.screenrecorder.service.RecordingCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    val uiState = RecordingStateRepository.uiState

    private val _recordings = MutableStateFlow<List<RecordingStore.RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingStore.RecordingItem>> = _recordings.asStateFlow()

    private val _overlayGranted = MutableStateFlow(false)
    val overlayGranted: StateFlow<Boolean> = _overlayGranted.asStateFlow()

    private val _settings = MutableStateFlow(RecorderSettingsRepository.load(getApplication()))
    val settings: StateFlow<RecorderSettings> = _settings.asStateFlow()

    init {
        refreshOverlayPermission()
        refreshRecordings()
    }

    fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            _recordings.value = RecordingStore.listRecordings(getApplication())
        }
    }

    fun refreshOverlayPermission(context: Context = getApplication()) {
        _overlayGranted.value = Settings.canDrawOverlays(context)
    }

    fun startRecording(context: Context, resultCode: Int, resultData: Intent) {
        RecordingCommands.start(context, resultCode, resultData, _settings.value)
    }

    fun stopRecording(context: Context) {
        RecordingCommands.stop(context)
    }

    fun showRecordingToolbox(
        context: Context,
        resultCode: Int? = null,
        resultData: Intent? = null,
    ) {
        FloatingControlsCommands.show(context, resultCode, resultData)
    }

    fun updateResolutionPreset(preset: ResolutionPreset) {
        updateSettings(_settings.value.copy(resolutionPreset = preset))
    }

    fun updateFrameRatePreset(preset: FrameRatePreset) {
        updateSettings(_settings.value.copy(frameRatePreset = preset))
    }

    fun updateCaptureSystemAudio(enabled: Boolean) {
        updateSettings(_settings.value.copy(captureSystemAudio = enabled))
    }

    fun deleteRecording(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            RecordingStore.deleteRecording(getApplication(), uri)
            _recordings.value = RecordingStore.listRecordings(getApplication())
        }
    }

    fun dismissError() {
        RecordingStateRepository.clearError()
    }

    fun reportError(message: String, lastOutputUri: String? = uiState.value.lastOutputUri) {
        RecordingStateRepository.setError(message, lastOutputUri)
    }

    private fun updateSettings(settings: RecorderSettings) {
        RecorderSettingsRepository.save(getApplication(), settings)
        _settings.value = settings
    }
}
