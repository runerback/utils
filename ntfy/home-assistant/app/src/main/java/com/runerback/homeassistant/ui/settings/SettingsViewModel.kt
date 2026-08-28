package com.runerback.homeassistant.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.homeassistant.data.local.SettingsRepository
import com.runerback.homeassistant.data.local.TokenRepository
import com.runerback.homeassistant.data.remote.ApiClient
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
) : ViewModel() {

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _hasToken = MutableStateFlow(false)
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _serverUrl.value = settingsRepository.serverUrl.first()
            _hasToken.value = tokenRepository.hasToken
        }
    }

    fun onServerUrlChange(value: String) {
        _serverUrl.value = value
        _saved.value = false
    }

    fun onTokenChange(value: String) {
        _token.value = value
        _saved.value = false
    }

    fun save() {
        viewModelScope.launch {
            _error.value = null
            val url = _serverUrl.value.trim().trimEnd('/')
            settingsRepository.setServerUrl(url)

            val newToken = _token.value.trim()
            val storedToken = tokenRepository.getToken()
            val tokenToSend = newToken.ifBlank { storedToken ?: "" }
            if (newToken.isNotBlank()) {
                tokenRepository.setToken(newToken)
                _hasToken.value = true
                _token.value = ""
            }

            val result = ApiClient.postMultipart(
                url,
                "/api/settings",
                mapOf(
                    "messages.server_url" to url,
                    "messages.token" to tokenToSend,
                )
            )
            result.onSuccess {
                LogBuffer.append("Settings saved: $url")
                _saved.value = true
            }.onFailure { error ->
                LogBuffer.append("Failed to save settings: ${error.message}")
                _error.value = error.message
            }
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            _hasToken.value = false
            _token.value = ""
            save()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                val context = application.applicationContext
                SettingsViewModel(
                    SettingsRepository(context),
                    TokenRepository(context),
                )
            }
        }
    }
}
