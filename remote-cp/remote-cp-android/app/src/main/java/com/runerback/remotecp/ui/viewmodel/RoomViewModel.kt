package com.runerback.remotecp.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.remotecp.data.model.Message
import com.runerback.remotecp.data.repository.MessageRepository
import com.runerback.remotecp.util.detectDeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class UiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,
    val backendUrl: String = "",
    val recentBackendUrls: List<String> = emptyList(),
    val markdownMode: Map<String, Boolean> = emptyMap()
)

@HiltViewModel
class RoomViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var socketJob: Job? = null

    init {
        val savedUrl = prefs.getString("backend_url", "http://10.0.2.2:5000") ?: "http://10.0.2.2:5000"
        _uiState.update { it.copy(backendUrl = savedUrl, recentBackendUrls = loadRecentBackendUrls()) }
        loadMessages()
        connectSocket()
    }

    private fun loadRecentBackendUrls(): List<String> {
        val json = prefs.getString("recent_backend_urls", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveRecentBackendUrls(urls: List<String>) {
        val array = JSONArray()
        urls.forEach { array.put(it) }
        prefs.edit().putString("recent_backend_urls", array.toString()).apply()
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            messageRepository.getMessages()
                .onSuccess { messages ->
                    _uiState.update { it.copy(messages = messages.reversed(), isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun connectSocket() {
        messageRepository.connectSocket(
            onConnect = {
                _uiState.update { it.copy(isConnected = true) }
                observeSocketMessages()
            },
            onDisconnect = {
                _uiState.update { it.copy(isConnected = false) }
            }
        )
    }

    private fun observeSocketMessages() {
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            messageRepository.observeMessages().collect { message ->
                _uiState.update { state ->
                    if (state.messages.any { it.id == message.id }) {
                        state
                    } else {
                        state.copy(messages = listOf(message) + state.messages)
                    }
                }
            }
        }
    }

    fun disconnectSocket() {
        socketJob?.cancel()
        messageRepository.disconnectSocket()
        _uiState.update { it.copy(isConnected = false) }
    }

    fun sendText(text: String, context: Context) {
        viewModelScope.launch {
            val deviceType = detectDeviceType(context)
            val timestamp = SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.getDefault()).format(Date())

            _uiState.update { it.copy(isLoading = true, error = null) }
            messageRepository.sendMessage(
                text = text,
                deviceType = deviceType,
                clientTimestamp = timestamp,
                images = null, videos = null, files = null,
                context = context
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Text sent.") }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun sendSharedMedia(uris: List<Uri>, context: Context) {
        val images = mutableListOf<Uri>()
        val videos = mutableListOf<Uri>()
        val files = mutableListOf<Uri>()

        val contentResolver = context.contentResolver
        for (uri in uris) {
            val mimeType = contentResolver.getType(uri)
            when {
                mimeType?.startsWith("image/") == true -> images.add(uri)
                mimeType?.startsWith("video/") == true -> videos.add(uri)
                else -> files.add(uri)
            }
        }

        sendMedia(
            images = images.ifEmpty { null },
            videos = videos.ifEmpty { null },
            files = files.ifEmpty { null },
            context = context
        )
    }

    fun sendMedia(
        images: List<Uri>?,
        videos: List<Uri>?,
        files: List<Uri>?,
        context: Context
    ) {
        viewModelScope.launch {
            val deviceType = detectDeviceType(context)
            val timestamp = SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.getDefault()).format(Date())

            _uiState.update { it.copy(isLoading = true, error = null) }
            messageRepository.sendMessage(
                text = null,
                deviceType = deviceType,
                clientTimestamp = timestamp,
                images = images,
                videos = videos,
                files = files,
                context = context
            ).onSuccess {
                val label = when {
                    !images.isNullOrEmpty() -> "Pictures sent."
                    !videos.isNullOrEmpty() -> "Videos sent."
                    else -> "Files sent."
                }
                _uiState.update { it.copy(isLoading = false, statusMessage = label) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateBackendUrl(url: String) {
        prefs.edit().putString("backend_url", url).apply()
        val updated = listOf(url) + _uiState.value.recentBackendUrls.filter { it != url }
        val trimmed = updated.take(5)
        saveRecentBackendUrls(trimmed)
        _uiState.update { it.copy(backendUrl = url, recentBackendUrls = trimmed, messages = emptyList(), error = null) }
        messageRepository.reconnect()
        loadMessages()
        disconnectSocket()
        connectSocket()
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, error = null) }
    }

    fun toggleMarkdownMode(messageId: String) {
        _uiState.update { state ->
            val current = state.markdownMode[messageId] ?: false
            state.copy(markdownMode = state.markdownMode + (messageId to !current))
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectSocket()
    }
}
