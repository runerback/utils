package com.runerback.brownnoise.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.runerback.brownnoise.streaming.ControlClient
import com.runerback.brownnoise.streaming.StreamState
import com.runerback.brownnoise.streaming.StreamingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(
        StreamUiState(
            host = prefs.getString(PREF_HOST, "10.0.2.2") ?: "10.0.2.2",
            port = prefs.getString(PREF_PORT, "54545") ?: "54545",
            volume = prefs.getFloat(PREF_VOLUME, 1.0f),
            noiseType = prefs.getString(PREF_NOISE_TYPE, "brown") ?: "brown",
            gain = prefs.getFloat(PREF_GAIN, 0.8f),
            surround = prefs.getFloat(PREF_SURROUND, 0.0f),
            reverb = prefs.getFloat(PREF_REVERB, 0.0f),
            softness = prefs.getFloat(PREF_SOFTNESS, 0.0f),
            wave = prefs.getBoolean(PREF_WAVE, false),
            waveRate = prefs.getFloat(PREF_WAVE_RATE, 0.5f)
        )
    )
    val uiState: StateFlow<StreamUiState> = _uiState

    private var streamingService: StreamingService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as StreamingService.LocalBinder
            streamingService = binder.getService().apply {
                setStateListener { state ->
                    viewModelScope.launch { handleStreamState(state) }
                }
            }
            streamingService?.setVolume(_uiState.value.volume)
            sendSettingsCommand()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            streamingService = null
        }
    }

    fun onHostChange(host: String) {
        _uiState.update { it.copy(host = host) }
    }

    fun onPortChange(port: String) {
        _uiState.update { it.copy(port = port) }
    }

    fun onVolumeChange(volume: Float) {
        _uiState.update { it.copy(volume = volume) }
        prefs.edit().putFloat(PREF_VOLUME, volume).apply()
        streamingService?.setVolume(volume)
    }

    fun onNoiseTypeChange(noiseType: String) {
        _uiState.update { it.copy(noiseType = noiseType) }
    }

    fun onGainChange(gain: Float) {
        _uiState.update { it.copy(gain = gain) }
    }

    fun onSurroundChange(surround: Float) {
        _uiState.update { it.copy(surround = surround) }
    }

    fun onReverbChange(reverb: Float) {
        _uiState.update { it.copy(reverb = reverb) }
    }

    fun onSoftnessChange(softness: Float) {
        _uiState.update { it.copy(softness = softness) }
    }

    fun onWaveChange(wave: Boolean) {
        _uiState.update { it.copy(wave = wave) }
    }

    fun onWaveRateChange(waveRate: Float) {
        _uiState.update { it.copy(waveRate = waveRate) }
    }

    fun applySettings() {
        prefs.edit()
            .putString(PREF_NOISE_TYPE, _uiState.value.noiseType)
            .putFloat(PREF_GAIN, _uiState.value.gain)
            .putFloat(PREF_SURROUND, _uiState.value.surround)
            .putFloat(PREF_REVERB, _uiState.value.reverb)
            .putFloat(PREF_SOFTNESS, _uiState.value.softness)
            .putBoolean(PREF_WAVE, _uiState.value.wave)
            .putFloat(PREF_WAVE_RATE, _uiState.value.waveRate)
            .apply()
        sendSettingsCommand(flush = true)
    }

    fun connect() {
        val state = _uiState.value
        var host = sanitizeHost(state.host)

        var port = state.port.trim().toIntOrNull()
        if (host.contains(":")) {
            val parts = host.split(":")
            host = parts[0]
            port = parts.getOrNull(1)?.toIntOrNull() ?: port
        }

        if (host.isEmpty() || port == null || port !in 1..65535) {
            _uiState.update { it.copy(status = "Bad address", error = "Enter a valid IP and port") }
            return
        }

        prefs.edit()
            .putString(PREF_HOST, host)
            .putString(PREF_PORT, port.toString())
            .apply()

        _uiState.update { it.copy(error = null) }

        val context = getApplication<Application>()
        val intent = Intent(context, StreamingService::class.java).apply {
            putExtra(StreamingService.EXTRA_HOST, host)
            putExtra(StreamingService.EXTRA_PORT, port)
            putExtra(StreamingService.EXTRA_VOLUME, state.volume)
        }
        ContextCompat.startForegroundService(context, intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun disconnect() {
        streamingService?.stopStreaming()
        val context = getApplication<Application>()
        try {
            context.unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
        }
        context.stopService(Intent(context, StreamingService::class.java))
        streamingService = null
        _uiState.update { it.copy(status = "Idle", isPlaying = false) }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun sendSettingsCommand(flush: Boolean = false) {
        val state = _uiState.value
        val host = sanitizeHost(state.host)
        val port = state.port.toIntOrNull() ?: return
        val controlPort = port + 1
        val command = buildMap<String, Any> {
            put("noise_type", state.noiseType)
            put("gain", state.gain)
            put("surround", state.surround)
            put("reverb", state.reverb)
            put("softness", state.softness)
            put("wave", state.wave)
            put("wave_rate", state.waveRate)
        }
        viewModelScope.launch(Dispatchers.IO) {
            ControlClient.sendCommand(host, controlPort, command)
                .onSuccess {
                    if (flush) {
                        streamingService?.flushAudio()
                    }
                    _uiState.update { it.copy(error = null) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message) }
                }
        }
    }

    private fun sanitizeHost(input: String): String {
        var host = input.trim()
        if (host.contains("://")) {
            host = host.substringAfter("://")
        }
        return host.substringBefore("/").substringBefore("?").substringBefore(":")
    }

    private fun handleStreamState(state: StreamState) {
        when (state) {
            is StreamState.Idle -> _uiState.update { it.copy(status = "Idle", isPlaying = false) }
            is StreamState.Connecting -> _uiState.update { it.copy(status = "Connecting", isPlaying = true) }
            is StreamState.Streaming -> _uiState.update { it.copy(status = "Streaming", isPlaying = true, error = null) }
            is StreamState.Error -> _uiState.update {
                it.copy(status = "Error", isPlaying = false, error = state.message)
            }
        }
    }

    companion object {
        private const val PREF_HOST = "server_host"
        private const val PREF_PORT = "server_port"
        private const val PREF_VOLUME = "playback_volume"
        private const val PREF_NOISE_TYPE = "noise_type"
        private const val PREF_GAIN = "gain"
        private const val PREF_SURROUND = "surround"
        private const val PREF_REVERB = "reverb"
        private const val PREF_SOFTNESS = "softness"
        private const val PREF_WAVE = "wave"
        private const val PREF_WAVE_RATE = "wave_rate"
    }
}

data class StreamUiState(
    val host: String = "",
    val port: String = "",
    val status: String = "Idle",
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val error: String? = null,
    val noiseType: String = "brown",
    val gain: Float = 0.8f,
    val surround: Float = 0.0f,
    val reverb: Float = 0.0f,
    val softness: Float = 0.0f,
    val wave: Boolean = false,
    val waveRate: Float = 0.5f
)
