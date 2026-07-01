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
import com.runerback.brownnoise.ui.logs.AppLogger
import com.runerback.brownnoise.ui.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(
        StreamUiState(
            host = prefs.getString(PREF_HOST, "10.0.2.2") ?: "10.0.2.2",
            port = prefs.getString(PREF_PORT, "54545") ?: "54545",
            volume = prefs.getFloat(PREF_VOLUME, 1.0f)
        )
    )
    val uiState: StateFlow<StreamUiState> = _uiState

    init {
        SettingsRepository.settings
            .onEach { settings ->
                trimWaveformBuffer(settings.waveformSamples)
                sendSettingsCommand(flush = true)
            }
            .launchIn(viewModelScope)
    }

    private var streamingService: StreamingService? = null
    private val waveformBuffer = ArrayDeque<Float>(MAX_WAVEFORM_SAMPLES)
    @Volatile
    private var lastWaveformUpdate = 0L

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppLogger.i("MainViewModel", "StreamingService connected")
            val binder = service as StreamingService.LocalBinder
            streamingService = binder.getService().apply {
                setStateListener { state ->
                    viewModelScope.launch { handleStreamState(state) }
                }
                setWaveformListener { frame ->
                    appendWaveform(frame)
                }
            }
            streamingService?.setVolume(_uiState.value.volume)
            sendSettingsCommand()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLogger.i("MainViewModel", "StreamingService disconnected")
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
            AppLogger.w("MainViewModel", "Bad address: host=$host port=$port")
            _uiState.update { it.copy(status = "Bad address", error = "Enter a valid IP and port") }
            return
        }

        AppLogger.i("MainViewModel", "Connecting to $host:$port")
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
        AppLogger.i("MainViewModel", "Disconnecting")
        streamingService?.stopStreaming()
        val context = getApplication<Application>()
        try {
            context.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            AppLogger.w("MainViewModel", "Service was not bound", e)
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
        val streamState = _uiState.value
        val settings = SettingsRepository.settings.value
        val host = sanitizeHost(streamState.host)
        val port = streamState.port.toIntOrNull() ?: return
        val controlPort = port + 1
        val command = buildMap<String, Any> {
            put("noise_type", settings.noiseType)
            put("gain", settings.gain)
            put("surround", settings.surround)
            put("reverb", settings.reverb)
            put("softness", settings.softness)
            put("wave", settings.wave)
            put("wave_rate", settings.waveRate)
        }
        AppLogger.i("MainViewModel", "Sending settings to $host:$controlPort: $command")
        viewModelScope.launch(Dispatchers.IO) {
            ControlClient.sendCommand(host, controlPort, command)
                .onSuccess {
                    AppLogger.i("MainViewModel", "Settings applied")
                    if (flush) {
                        streamingService?.flushAudio()
                    }
                    _uiState.update { it.copy(error = null) }
                }
                .onFailure { err ->
                    AppLogger.w("MainViewModel", "Failed to apply settings: ${err.message}", err)
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

    private fun appendWaveform(frame: FloatArray) {
        val settings = SettingsRepository.settings.value
        if (!settings.waveformEnabled) return
        val capacity = settings.waveformSamples
        synchronized(waveformBuffer) {
            for (value in frame) {
                if (waveformBuffer.size >= capacity) waveformBuffer.removeFirst()
                waveformBuffer.addLast(value)
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastWaveformUpdate < 33) return
        lastWaveformUpdate = now
        emitWaveformSnapshot()
    }

    private fun trimWaveformBuffer(capacity: Int) {
        val trimmed = synchronized(waveformBuffer) {
            while (waveformBuffer.size > capacity) waveformBuffer.removeFirst()
            waveformBuffer.toList()
        }
        _uiState.update { it.copy(waveformPoints = trimmed) }
    }

    private fun emitWaveformSnapshot() {
        val snapshot = synchronized(waveformBuffer) { waveformBuffer.toList() }
        _uiState.update { it.copy(waveformPoints = snapshot) }
    }

    private fun handleStreamState(state: StreamState) {
        when (state) {
            is StreamState.Idle -> {
                AppLogger.i("MainViewModel", "Stream idle")
                _uiState.update { it.copy(status = "Idle", isPlaying = false) }
            }
            is StreamState.Connecting -> {
                AppLogger.i("MainViewModel", "Stream connecting")
                _uiState.update { it.copy(status = "Connecting", isPlaying = true) }
            }
            is StreamState.Streaming -> {
                AppLogger.i("MainViewModel", "Stream streaming")
                _uiState.update { it.copy(status = "Streaming", isPlaying = true, error = null) }
            }
            is StreamState.Error -> {
                AppLogger.e("MainViewModel", "Stream error: ${state.message}")
                _uiState.update {
                    it.copy(status = "Error", isPlaying = false, error = state.message)
                }
            }
        }
    }

    companion object {
        private const val PREF_HOST = "server_host"
        private const val PREF_PORT = "server_port"
        private const val PREF_VOLUME = "playback_volume"
    }
}

data class StreamUiState(
    val host: String = "",
    val port: String = "",
    val status: String = "Idle",
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val error: String? = null,
    val waveformPoints: List<Float> = emptyList()
)
