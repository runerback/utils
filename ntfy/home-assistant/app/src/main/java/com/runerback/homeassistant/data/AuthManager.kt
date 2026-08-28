package com.runerback.homeassistant.data

import com.runerback.homeassistant.data.remote.ApiClient
import com.runerback.homeassistant.data.remote.model.User
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthManager {

    private val _username = MutableStateFlow<String?>(null)
    val username: StateFlow<String?> = _username.asStateFlow()

    suspend fun checkAuth(baseUrl: String): Result<User> {
        LogBuffer.append("Checking auth at $baseUrl")
        return ApiClient.get<User>(baseUrl, "/api/me")
            .onSuccess { user ->
                LogBuffer.append("Auth OK: ${user.username}")
                _username.value = user.username
                ApiClient.fetchCsrf(baseUrl)
            }
            .onFailure {
                LogBuffer.append("Auth check failed: ${it.message}")
                _username.value = null
            }
    }

    suspend fun login(baseUrl: String, username: String, password: String): Result<Unit> {
        LogBuffer.append("Logging in as $username")
        return ApiClient.postForm(
            baseUrl,
            "/api/login",
            mapOf("username" to username, "password" to password),
        ).mapCatching {
            _username.value = username
            ApiClient.fetchCsrf(baseUrl).getOrThrow()
            Unit
        }.onSuccess {
            LogBuffer.append("Login success: $username")
        }.onFailure {
            LogBuffer.append("Login failed: ${it.message}")
            _username.value = null
        }
    }

    suspend fun logout(baseUrl: String): Result<Unit> {
        LogBuffer.append("Logging out")
        return ApiClient.postForm(baseUrl, "/api/logout", emptyMap())
            .map { }
            .onSuccess {
                LogBuffer.append("Logout success")
                _username.value = null
            }
            .onFailure {
                LogBuffer.append("Logout failed: ${it.message}")
            }
    }

    fun onUnauthorized() {
        LogBuffer.append("Unauthorized, clearing session")
        _username.value = null
    }
}
