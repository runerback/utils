package com.runerback.homeassistant.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.homeassistant.data.local.SettingsRepository
import com.runerback.homeassistant.data.remote.ApiClient
import com.runerback.homeassistant.data.remote.model.SystemInfo
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _systemInfo = MutableStateFlow<SystemInfo?>(null)
    val systemInfo: StateFlow<SystemInfo?> = _systemInfo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            while (true) {
                val baseUrl = settingsRepository.serverUrl.first()
                ApiClient.get<SystemInfo>(baseUrl, "/api/system")
                    .onSuccess { info ->
                        LogBuffer.append("System info loaded: CPU ${info.cpuTemp}°C")
                        _systemInfo.value = info
                        _error.value = null
                    }
                    .onFailure { error ->
                        LogBuffer.append("System info failed: ${error.message}")
                        _error.value = error.message
                    }
                delay(30_000)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                HomeViewModel(SettingsRepository(application.applicationContext))
            }
        }
    }
}
