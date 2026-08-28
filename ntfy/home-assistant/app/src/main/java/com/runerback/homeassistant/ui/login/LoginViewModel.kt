package com.runerback.homeassistant.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.homeassistant.data.AuthManager
import com.runerback.homeassistant.data.local.SettingsRepository
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onUsernameChange(value: String) {
        _username.value = value
        _error.value = null
    }

    fun onPasswordChange(value: String) {
        _password.value = value
        _error.value = null
    }

    fun login() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            val result = AuthManager.login(baseUrl, _username.value.trim(), _password.value)
            result.onFailure { error ->
                LogBuffer.append("Login error for ${_username.value}: ${error.message}")
                _error.value = error.message ?: "Login failed"
            }
            _loading.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                LoginViewModel(SettingsRepository(application.applicationContext))
            }
        }
    }
}
