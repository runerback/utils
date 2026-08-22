package com.runerback.ntfyclient.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ntfyclient.data.local.SettingsRepository
import com.runerback.ntfyclient.data.local.TokenRepository
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

    private val _downloadAttachmentsUnmeteredOnly = MutableStateFlow(true)
    val downloadAttachmentsUnmeteredOnly: StateFlow<Boolean> = _downloadAttachmentsUnmeteredOnly.asStateFlow()

    init {
        viewModelScope.launch {
            _serverUrl.value = settingsRepository.serverUrl.first()
            _hasToken.value = tokenRepository.hasToken
            _downloadAttachmentsUnmeteredOnly.value =
                settingsRepository.downloadAttachmentsUnmeteredOnly.first()
        }
    }

    fun onServerUrlChange(url: String) {
        _serverUrl.value = url
    }

    fun onTokenChange(token: String) {
        _token.value = token
    }

    fun onDownloadAttachmentsUnmeteredOnlyChange(value: Boolean) {
        _downloadAttachmentsUnmeteredOnly.value = value
    }

    fun save() {
        viewModelScope.launch {
            settingsRepository.setServerUrl(_serverUrl.value.trim())
            settingsRepository.setDownloadAttachmentsUnmeteredOnly(_downloadAttachmentsUnmeteredOnly.value)
            val trimmedToken = _token.value.trim()
            if (trimmedToken.isNotBlank()) {
                tokenRepository.setToken(trimmedToken)
                _hasToken.value = true
                _token.value = ""
            }
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            _hasToken.value = false
            _token.value = ""
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
