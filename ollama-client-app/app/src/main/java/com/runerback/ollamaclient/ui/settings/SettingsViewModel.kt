package com.runerback.ollamaclient.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ollamaclient.data.local.SettingsRepository
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

    private val _think = MutableStateFlow(false)
    val think: StateFlow<Boolean> = _think.asStateFlow()

    init {
        viewModelScope.launch {
            _serverUrl.value = settingsRepository.serverUrl.first()
            _think.value = settingsRepository.think.first()
        }
    }

    fun onServerUrlChange(url: String) {
        _serverUrl.value = url
    }

    fun onThinkChange(value: Boolean) {
        _think.value = value
    }

    fun save() {
        viewModelScope.launch {
            settingsRepository.setServerUrl(_serverUrl.value.trim())
            settingsRepository.setThink(_think.value)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                SettingsViewModel(
                    SettingsRepository(application.applicationContext),
                )
            }
        }
    }
}
