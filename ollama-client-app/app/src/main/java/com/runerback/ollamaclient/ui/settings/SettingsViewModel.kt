package com.runerback.ollamaclient.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ollamaclient.data.local.SettingsDataStore
import com.runerback.ollamaclient.data.local.SettingsRepository
import com.runerback.ollamaclient.data.remote.OllamaApiService
import com.runerback.ollamaclient.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val ollamaApiService: OllamaApiService = OllamaApiService(),
) : ViewModel() {

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _modelsError = MutableStateFlow("")
    val modelsError: StateFlow<String> = _modelsError.asStateFlow()

    private val _think = MutableStateFlow(false)
    val think: StateFlow<Boolean> = _think.asStateFlow()

    init {
        viewModelScope.launch {
            _serverUrl.value = settingsRepository.serverUrl.first()
            _model.value = settingsRepository.model.first()
            _think.value = settingsRepository.think.first()
            loadModels()
        }
    }

    fun onServerUrlChange(url: String) {
        _serverUrl.value = url
    }

    fun onModelChange(value: String) {
        _model.value = value
    }

    fun onThinkChange(value: Boolean) {
        _think.value = value
    }

    fun loadModels() {
        viewModelScope.launch {
            _isLoadingModels.value = true
            _modelsError.value = ""
            try {
                val availableModels = ollamaApiService.listModels(_serverUrl.value.trim())
                _models.value = availableModels
                if (_model.value.isBlank() && availableModels.isNotEmpty()) {
                    _model.value = availableModels.first()
                }
                LogBuffer.append("Loaded ${availableModels.size} models")
            } catch (e: Exception) {
                val message = e.message ?: e.toString()
                _modelsError.value = message
                LogBuffer.append("Failed to load models: $message")
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            settingsRepository.setServerUrl(_serverUrl.value.trim())
            settingsRepository.setModel(_model.value)
            settingsRepository.setThink(_think.value)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                SettingsViewModel(
                    SettingsDataStore(application.applicationContext),
                )
            }
        }
    }
}
