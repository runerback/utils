package com.runerback.videohelper.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.videohelper.data.settings.ServerSettings
import com.runerback.videohelper.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settingsRepository.settings.first()
            _serverUrl.value = current.serverUrl
            _token.value = current.token
        }
    }

    fun onServerUrlChange(value: String) {
        _serverUrl.value = value
    }

    fun onTokenChange(value: String) {
        _token.value = value
    }

    fun save() {
        viewModelScope.launch {
            settingsRepository.save(
                ServerSettings(
                    serverUrl = _serverUrl.value.trim(),
                    token = _token.value.trim(),
                )
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                SettingsViewModel(SettingsRepository(application.applicationContext))
            }
        }
    }
}
