package com.runerback.homeassistant.ui.messages

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.homeassistant.data.local.SettingsRepository
import com.runerback.homeassistant.data.remote.ApiClient
import com.runerback.homeassistant.data.remote.SseClient
import com.runerback.homeassistant.data.remote.model.Message
import com.runerback.homeassistant.data.remote.model.Topic
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(
    private val topic: Topic,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var sseJob: Job? = null

    init {
        loadHistory()
        connectSse()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.get<List<Message>>(baseUrl, "/api/topics/${topic.id}/messages")
                .onSuccess {
                    LogBuffer.append("Loaded ${it.size} messages for topic ${topic.name}")
                    _messages.value = it
                }
                .onFailure {
                    LogBuffer.append("Failed to load messages for topic ${topic.name}: ${it.message}")
                    _error.value = it.message
                }
        }
    }

    private fun connectSse() {
        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            val baseUrl = settingsRepository.serverUrl.first()
            LogBuffer.append("Connecting SSE for topic ${topic.name}")
            SseClient.subscribe(baseUrl, topic.name).collect { message ->
                LogBuffer.append("Received SSE message for topic ${topic.name}")
                _messages.value = _messages.value + message
            }
        }
    }

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun send() {
        val body = _input.value.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.postForm(
                baseUrl,
                "/api/topics/${topic.id}/messages",
                mapOf("body" to body)
            ).onSuccess {
                LogBuffer.append("Sent message to topic ${topic.name}")
                _messages.value = _messages.value + Message(
                    sender = null,
                    body = body,
                    sentAt = java.time.Instant.now().toString(),
                    isMine = true
                )
                _input.value = ""
            }.onFailure {
                LogBuffer.append("Failed to send message to topic ${topic.name}: ${it.message}")
                _error.value = it.message
            }
            _sending.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseJob?.cancel()
    }

    companion object {
        fun Factory(topic: Topic): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                ChatViewModel(topic, SettingsRepository(application.applicationContext))
            }
        }
    }
}
