package com.runerback.ollamaclient.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ollamaclient.data.local.SettingsRepository
import com.runerback.ollamaclient.data.model.Message
import com.runerback.ollamaclient.data.remote.OllamaApiService
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
            val think = settingsRepository.think.first()
            val userMessage = Message(role = "user", content = text.trim())
            val currentMessages = _messages.value + userMessage
            _messages.value = currentMessages + Message(role = "assistant", content = "")
            _isLoading.value = true

            try {
                ollamaApiService.chat(
                    baseUrl = serverUrl,
                    model = DEFAULT_MODEL,
                    messages = currentMessages,
                    think = think,
                ).collect { assistantMessage ->
                    _messages.value = _messages.value.dropLast(1) + assistantMessage
                }
            } catch (e: Exception) {
                _messages.value = _messages.value.dropLast(1) +
                    Message(role = "assistant", content = "Error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        private const val DEFAULT_MODEL = "deepseek-r1"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                ChatViewModel(
                    settingsRepository = SettingsRepository(application.applicationContext),
                )
            }
        }
    }
}
