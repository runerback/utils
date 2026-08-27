package com.runerback.ntfymgr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ntfymgr.NtfyApplication
import com.runerback.ntfymgr.data.local.SettingsRepository
import com.runerback.ntfymgr.data.local.TokenRepository
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

    private val _hasToken = MutableStateFlow(false)
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    private var originalServerUrl: String = ""

    private val _serverUrlChanged = MutableStateFlow(false)
    val serverUrlChanged: StateFlow<Boolean> = _serverUrlChanged.asStateFlow()

    init {
        viewModelScope.launch {
            originalServerUrl = settingsRepository.serverUrl.first()
            _serverUrl.value = originalServerUrl
            _hasToken.value = tokenRepository.hasToken
        }
    }

    fun onServerUrlChange(url: String) {
        _serverUrl.value = url
    }

    fun save() {
        viewModelScope.launch {
            val newUrl = _serverUrl.value.trim().trimEnd('/')
            val changed = newUrl != originalServerUrl
            if (changed) {
                settingsRepository.setServerUrl(newUrl)
                tokenRepository.clearToken()
                _hasToken.value = false
                _serverUrlChanged.value = true
                originalServerUrl = newUrl
            }
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            _hasToken.value = false
        }
    }

    fun consumeServerUrlChanged() {
        _serverUrlChanged.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                val context = application.applicationContext
                val ntfyApplication = application as NtfyApplication
                SettingsViewModel(
                    ntfyApplication.settingsRepository,
                    ntfyApplication.tokenRepository,
                )
            }
        }
    }
}
