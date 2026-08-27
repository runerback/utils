package com.runerback.ntfymgr.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runerback.ntfymgr.data.local.SettingsRepository
import com.runerback.ntfymgr.data.local.TokenRepository
import com.runerback.ntfymgr.data.remote.NtfyMgrApi
import com.runerback.ntfymgr.data.remote.TopicItem
import com.runerback.ntfymgr.data.remote.UserItem
import com.runerback.ntfymgr.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
            val url = settingsRepository.serverUrl.first()
            _serverUrl.value = url
            LogBuffer.append("Loaded server URL: $url")
            if (tokenRepository.hasToken) {
                load()
            } else {
                _uiState.value = UiState.Login
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val url = _serverUrl.value
            if (url.isBlank()) {
                _uiState.value = UiState.Error("Server URL not configured. Open Settings.")
                return@launch
            }
            LogBuffer.append("Logging in to $url as $username")
            val result = api.login(url, username, password)
            result.fold(
                onSuccess = { response ->
                    tokenRepository.setToken(response.token)
                    api.token = response.token
                    LogBuffer.append("Login succeeded")
                    load()
                },
                onFailure = { error ->
                    LogBuffer.append("Login failed: ${error.message}")
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
            LogBuffer.append("Logged out")
            _uiState.value = UiState.Login
        }
    }

    fun load() {
        viewModelScope.launch {
            val url = _serverUrl.value
            if (url.isBlank()) {
                _uiState.value = UiState.Error("Server URL not configured. Open Settings.")
                return@launch
            }
            val current = _uiState.value
            if (current !is UiState.Ready) {
                _uiState.value = UiState.Loading
            }
            LogBuffer.append("Loading users and topics from $url")
            val usersResult = api.listUsers(url)
            val topicsResult = api.listTopics(url)
            val usersError = usersResult.exceptionOrNull()
            val topicsError = topicsResult.exceptionOrNull()
            val error = usersError ?: topicsError
            if (error != null) {
                LogBuffer.append("Load failed: ${error.message}")
                if (current is UiState.Ready) {
                    showError(error.message ?: "Load failed")
                } else if (error.message?.contains("401") == true) {
                    tokenRepository.clearToken()
                    api.token = null
                    _uiState.value = UiState.Login
                } else {
                    _uiState.value = UiState.Error(error.message ?: "Load failed")
                }
                return@launch
            }
            LogBuffer.append("Loaded users and topics")
            _uiState.value = UiState.Ready(
                users = usersResult.getOrDefault(emptyList()),
                topics = topicsResult.getOrDefault(emptyList()),
            )
        }
    }

    fun refreshServerUrl() {
        viewModelScope.launch {
            val url = settingsRepository.serverUrl.first()
            _serverUrl.value = url
            LogBuffer.append("Server URL updated: $url")
        }
    }

    fun onServerUrlChanged() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            api.token = null
            val url = settingsRepository.serverUrl.first()
            _serverUrl.value = url
            LogBuffer.append("Server URL changed to $url; token cleared")
            _uiState.value = UiState.Login
        }
    }

    fun createUser(username: String, password: String) {
        viewModelScope.launch {
            api.createUser(_serverUrl.value, username, password)
                .onSuccess { showMessage(it.detail) }
                .onFailure { showError(it.message ?: "Failed to create user") }
            load()
        }
    }

    fun deleteUser(name: String) {
        viewModelScope.launch {
            api.deleteUser(_serverUrl.value, name)
                .onSuccess {
                    showMessage(it.detail)
                    updateState { state ->
                        state.copy(
                            users = state.users.filter { it.name != name },
                            topics = state.topics.map { topic ->
                                topic.copy(accessors = topic.accessors.filter { it.username != name })
                            }
                        )
                    }
                }
                .onFailure { showError(it.message ?: "Failed to delete user") }
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
                .onSuccess {
                    showMessage(it.detail)
                    updateState { state ->
                        state.copy(
                            users = state.users.map { user ->
                                if (user.name == name) {
                                    user.copy(accesses = user.accesses.filter { it.topic != topic })
                                } else {
                                    user
                                }
                            },
                            topics = state.topics.map { item ->
                                if (item.name == topic) {
                                    item.copy(accessors = item.accessors.filter { it.username != name })
                                } else {
                                    item
                                }
                            }
                        )
                    }
                }
                .onFailure { showError(it.message ?: "Failed to revoke access") }
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
                .onSuccess {
                    showMessage(it.detail)
                    updateUsers { users ->
                        users.map { user ->
                            if (user.name == name) {
                                user.copy(tokens = user.tokens.filter { it.value != token })
                            } else {
                                user
                            }
                        }
                    }
                }
                .onFailure { showError(it.message ?: "Failed to delete token") }
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
                .onSuccess {
                    showMessage(it.detail)
                    updateState { state ->
                        state.copy(
                            topics = state.topics.filter { it.name != topic },
                            users = state.users.map { user ->
                                user.copy(accesses = user.accesses.filter { it.topic != topic })
                            }
                        )
                    }
                }
                .onFailure { showError(it.message ?: "Failed to delete topic") }
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
                .onSuccess {
                    showMessage(it.detail)
                    updateState { state ->
                        state.copy(
                            topics = state.topics.map { item ->
                                if (item.name == topic) {
                                    item.copy(accessors = item.accessors.filter { it.username != username })
                                } else {
                                    item
                                }
                            },
                            users = state.users.map { user ->
                                if (user.name == username) {
                                    user.copy(accesses = user.accesses.filter { it.topic != topic })
                                } else {
                                    user
                                }
                            }
                        )
                    }
                }
                .onFailure { showError(it.message ?: "Failed to revoke access") }
        }
    }

    private fun updateUsers(transform: (List<UserItem>) -> List<UserItem>) {
        val current = _uiState.value
        if (current is UiState.Ready) {
            _uiState.value = current.copy(users = transform(current.users))
        }
    }

    private fun updateTopics(transform: (List<TopicItem>) -> List<TopicItem>) {
        val current = _uiState.value
        if (current is UiState.Ready) {
            _uiState.value = current.copy(topics = transform(current.topics))
        }
    }

    private fun updateState(transform: (UiState.Ready) -> UiState.Ready) {
        val current = _uiState.value
        if (current is UiState.Ready) {
            _uiState.value = transform(current)
        }
    }

    fun clearMessage() {
        val current = _uiState.value
        if (current is UiState.Ready) {
            _uiState.value = current.copy(message = null)
        }
    }

    fun showMessage(message: String) {
        LogBuffer.append(message)
        val current = _uiState.value
        if (current is UiState.Ready) {
            _uiState.value = current.copy(message = message)
        }
    }

    private fun showError(message: String) {
        LogBuffer.append("Error: $message")
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
