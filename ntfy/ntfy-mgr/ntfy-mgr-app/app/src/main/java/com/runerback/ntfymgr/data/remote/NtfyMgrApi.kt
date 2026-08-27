package com.runerback.ntfymgr.data.remote

import com.runerback.ntfymgr.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

class NtfyMgrApi {

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mediaType = "application/json".toMediaType()

    var token: String? = null

    suspend fun login(serverUrl: String, username: String, password: String): Result<LoginResponse> =
        post(serverUrl, "/auth/login", LoginRequest(username, password))

    suspend fun logout(serverUrl: String): Result<Unit> =
        post(serverUrl, "/auth/logout")

    suspend fun listUsers(serverUrl: String): Result<List<UserItem>> =
        get(serverUrl, "/users")

    suspend fun createUser(serverUrl: String, username: String, password: String): Result<MessageResponse> =
        post(serverUrl, "/users", UserCreateRequest(username, password))

    suspend fun deleteUser(serverUrl: String, name: String): Result<MessageResponse> =
        delete(serverUrl, "/users/${name.urlEncode()}")

    suspend fun grantUserAccess(serverUrl: String, name: String, topic: String, permission: String): Result<MessageResponse> =
        post(serverUrl, "/users/${name.urlEncode()}/access", AccessRequest(topic, permission))

    suspend fun revokeUserAccess(serverUrl: String, name: String, topic: String): Result<MessageResponse> =
        delete(serverUrl, "/users/${name.urlEncode()}/access/${topic.urlEncode()}")

    suspend fun createUserToken(serverUrl: String, name: String, expires: String, label: String): Result<MessageResponse> =
        post(serverUrl, "/users/${name.urlEncode()}/tokens", TokenCreateRequest(expires, label))

    suspend fun deleteUserToken(serverUrl: String, name: String, token: String): Result<MessageResponse> =
        delete(serverUrl, "/users/${name.urlEncode()}/tokens/${token.urlEncode()}")

    suspend fun listTopics(serverUrl: String): Result<List<TopicItem>> =
        get(serverUrl, "/topics")

    suspend fun grantTopicAccess(serverUrl: String, topic: String, username: String, permission: String): Result<MessageResponse> =
        post(serverUrl, "/topics/${topic.urlEncode()}/access", TopicAccessRequest(username, permission))

    suspend fun revokeTopicAccess(serverUrl: String, topic: String, username: String): Result<MessageResponse> =
        delete(serverUrl, "/topics/${topic.urlEncode()}/access/${username.urlEncode()}")

    suspend fun deleteTopic(serverUrl: String, topic: String): Result<MessageResponse> =
        delete(serverUrl, "/topics/${topic.urlEncode()}")

    private inline fun <reified T> Request.Builder.json(body: T): Request.Builder {
        val bodyText = json.encodeToString(body)
        return post(bodyText.toRequestBody(mediaType))
    }

    private suspend inline fun <reified T> get(serverUrl: String, path: String): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$serverUrl$path")
                    .auth()
                    .build()
                executeJson(request)
            }
        }

    private suspend inline fun <reified T, reified B> post(serverUrl: String, path: String, body: B): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$serverUrl$path")
                    .auth()
                    .json(body)
                    .build()
                executeJson(request)
            }
        }

    private suspend inline fun <reified T> post(serverUrl: String, path: String): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$serverUrl$path")
                    .auth()
                    .post("".toRequestBody(mediaType))
                    .build()
                executeJson(request)
            }
        }

    private suspend inline fun <reified T> delete(serverUrl: String, path: String): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$serverUrl$path")
                    .auth()
                    .delete()
                    .build()
                executeJson(request)
            }
        }

    private inline fun <reified T> executeJson(request: Request): T {
        LogBuffer.append("${request.method} ${request.url}")
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string()
            if (!response.isSuccessful) {
                val detail = try {
                    json.decodeFromString<MessageResponse>(bodyText ?: "{}").detail
                } catch (_: Exception) {
                    bodyText ?: response.message
                }
                LogBuffer.append("Response ${response.code}: $detail")
                throw IOException("${response.code}: $detail")
            }
            LogBuffer.append("Response ${response.code}")
            return json.decodeFromString(bodyText ?: throw IOException("Empty response"))
        }
    }

    private fun Request.Builder.auth(): Request.Builder {
        token?.let {
            header("Authorization", "Bearer $it")
            LogBuffer.append("Added Authorization header")
        } ?: LogBuffer.append("No token available")
        return this
    }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
