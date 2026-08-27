package com.runerback.ntfymgr.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runerback.ntfymgr.data.local.SettingsRepository
import com.runerback.ntfymgr.data.local.TokenRepository
import com.runerback.ntfymgr.data.remote.NtfyMgrApi
import com.runerback.ntfymgr.data.remote.TopicItem
import com.runerback.ntfymgr.data.remote.UserItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Login : UiState
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Ready(
        val users: List<UserItem>,
        val topics: List<TopicItem>,
        val message: String? = null,
    ) : UiState
}

class HomeViewModel(
    private val api: NtfyMgrApi,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl

    init {
        viewModelScope.launch {
            settingsRepository.serverUrl.collect { url ->
                _serverUrl.value = url
                if (_uiState.value == UiState.Loading && url.isNotBlank()) {
                    if (tokenRepository.hasToken) {
                        load()
                    } else {
                        _uiState.value = UiState.Login
                    }
                }
            }
        }
    }

    fun setServerUrl(url: String) {
        _serverUrl.value = url.trim().trimEnd('/')
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val url = _serverUrl.value
            if (url.isBlank()) {
                _uiState.value = UiState.Error("Server URL is required")
                return@launch
            }
            val result = api.login(url, username, password)
            result.fold(
                onSuccess = { response ->
                    tokenRepository.setToken(response.token)
                    api.token = response.token
                    load()
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Login failed")
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            val url = _serverUrl.value
            if (url.isNotBlank()) {
                api.logout(url)
            }
            tokenRepository.clearToken()
            api.token = null
            _uiState.value = UiState.Login
        }
    }

    fun load() {
        viewModelScope.launch {
            val url = _serverUrl.value
            if (url.isBlank()) {
                _uiState.value = UiState.Error("Server URL is required")
                return@launch
            }
            _uiState.value = UiState.Loading
            val usersResult = api.listUsers(url)
            val topicsResult = api.listTopics(url)
            val error = usersResult.exceptionOrNull()?.message ?: topicsResult.exceptionOrNull()?.message
            if (error != null) {
                if (error.contains("401") || error.contains("Unauthorized")) {
                    tokenRepository.clearToken()
                    api.token = null
                    _uiState.value = UiState.Login
                } else {
                    _uiState.value = UiState.Error(error)
                }
                return@launch
            }
            _uiState.value = UiState.Ready(
                users = usersResult.getOrDefault(emptyList()),
                topics = topicsResult.getOrDefault(emptyList()),
            )
        }
    }

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setServerUrl(url)
            setServerUrl(url)
        }
    }

    fun createUser(username: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            api.createUser(_serverUrl.value, username, password)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to create user") }
            onDone()
        }
    }

    fun deleteUser(name: String) {
        viewModelScope.launch {
            api.deleteUser(_serverUrl.value, name)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to delete user") }
            load()
        }
    }

    fun grantUserAccess(name: String, topic: String, permission: String) {
        viewModelScope.launch {
            api.grantUserAccess(_serverUrl.value, name, topic, permission)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to grant access") }
            load()
        }
    }

    fun revokeUserAccess(name: String, topic: String) {
        viewModelScope.launch {
            api.revokeUserAccess(_serverUrl.value, name, topic)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to revoke access") }
            load()
        }
    }

    fun createUserToken(name: String, expires: String, label: String) {
        viewModelScope.launch {
            api.createUserToken(_serverUrl.value, name, expires, label)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to create token") }
            load()
        }
    }

    fun deleteUserToken(name: String, token: String) {
        viewModelScope.launch {
            api.deleteUserToken(_serverUrl.value, name, token)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to delete token") }
            load()
        }
    }

    fun createTopic(topic: String, username: String, permission: String) {
        viewModelScope.launch {
            api.grantTopicAccess(_serverUrl.value, topic, username, permission)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to create topic") }
            load()
        }
    }

    fun deleteTopic(topic: String) {
        viewModelScope.launch {
            api.deleteTopic(_serverUrl.value, topic)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to delete topic") }
            load()
        }
    }

    fun grantTopicAccess(topic: String, username: String, permission: String) {
        viewModelScope.launch {
            api.grantTopicAccess(_serverUrl.value, topic, username, permission)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to grant access") }
            load()
        }
    }

    fun revokeTopicAccess(topic: String, username: String) {
        viewModelScope.launch {
            api.revokeTopicAccess(_serverUrl.value, topic, username)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to revoke access") }
            load()
        }
    }

    private fun showMessage(message: String) {
        val current = _uiState.value
        if (current is UiState.Ready) {
            _uiState.value = current.copy(message = message)
        }
    }

    private fun showError(message: String) {
        val current = _uiState.value
        if (current is UiState.Ready) {
            _uiState.value = current.copy(message = "Error: $message")
        }
    }

    class Factory(
        private val api: NtfyMgrApi,
        private val tokenRepository: TokenRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(api, tokenRepository, settingsRepository) as T
        }
    }
}
