package com.runerback.ntfyclient.ui.send

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ntfyclient.data.local.MessageRepository
import com.runerback.ntfyclient.data.local.SettingsRepository
import com.runerback.ntfyclient.data.local.TokenRepository
import com.runerback.ntfyclient.data.local.db.MessageEntity
import com.runerback.ntfyclient.data.remote.NtfyPublishApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SendState {
    data object Idle : SendState()
    data object Sending : SendState()
    data object Success : SendState()
    data class Error(val message: String) : SendState()
}

class SendViewModel private constructor(
    private val topic: String,
    context: Context,
) : ViewModel() {

    private val messageRepository = MessageRepository(context)
    private val settingsRepository = SettingsRepository(context)
    private val tokenRepository = TokenRepository(context)
    private val api = NtfyPublishApi()

    val messages: StateFlow<List<MessageEntity>> = messageRepository
        .messagesForTopic(topic)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()

    fun send(message: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _sendState.value = SendState.Sending
            val serverUrl = settingsRepository.serverUrl.first()
            val token = tokenRepository.getToken()

            api.publish(serverUrl, topic, token, trimmed)
                .onSuccess { response ->
                    messageRepository.insertFromNtfy(topic, response)
                    _sendState.value = SendState.Success
                }
                .onFailure { error ->
                    _sendState.value = SendState.Error(error.message ?: "Unknown error")
                }
        }
    }

    fun resetSendState() {
        _sendState.value = SendState.Idle
    }

    companion object {
        fun Factory(topic: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                SendViewModel(topic, application.applicationContext)
            }
        }
    }
}
