package com.runerback.ollamaclient.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ollamaclient.data.local.SettingsDataStore
import com.runerback.ollamaclient.data.local.SettingsRepository
import com.runerback.ollamaclient.data.model.Message
import com.runerback.ollamaclient.data.remote.OllamaApiService
import com.runerback.ollamaclient.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(
    private val settingsRepository: SettingsRepository,
    private val ollamaApiService: OllamaApiService = OllamaApiService(),
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun send(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val serverUrl = settingsRepository.serverUrl.first()
            val model = settingsRepository.model.first()
            val think = settingsRepository.think.first()

            if (model.isBlank()) {
                LogBuffer.append("No model selected; aborting send")
                _messages.value = _messages.value +
                    Message(role = "assistant", content = "Error: no model selected. Open Settings and choose a model.")
                return@launch
            }

            val userMessage = Message(role = "user", content = text.trim())
            val currentMessages = _messages.value + userMessage
            val assistantMessage = Message(role = "assistant", content = "")
            _messages.value = currentMessages + assistantMessage
            _isLoading.value = true

            try {
                LogBuffer.append("Sending message to $serverUrl using model=$model think=$think")
                ollamaApiService.chat(
                    baseUrl = serverUrl,
                    model = model,
                    messages = currentMessages,
                    think = think,
                ).collect { chunk ->
                    val lastMessage = _messages.value.lastOrNull() ?: assistantMessage
                    _messages.value = _messages.value.dropLast(1) + lastMessage.copy(
                        content = chunk.content,
                        thinking = chunk.thinking,
                    )
                }
            } catch (e: Exception) {
                val message = e.message ?: e.toString()
                LogBuffer.append("Chat failed: $message")
                _messages.value = _messages.value.dropLast(1) +
                    assistantMessage.copy(content = "Error: $message")
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                ChatViewModel(
                    settingsRepository = SettingsDataStore(application.applicationContext),
                )
            }
        }
    }
}
