package com.runerback.homeassistant.data.remote

import com.runerback.homeassistant.data.AuthManager
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

object ApiClient {

    internal val client = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val json = Json { ignoreUnknownKeys = true }

    internal var csrfToken: String = ""

    suspend fun fetchCsrf(baseUrl: String): Result<String> = get(baseUrl, "/api/csrf") { body ->
        val response = json.decodeFromString(CsrfResponse.serializer(), body)
        csrfToken = response.csrfToken
        csrfToken
    }

    suspend inline fun <reified T> get(baseUrl: String, path: String): Result<T> =
        request(baseUrl, path) { url ->
            Request.Builder().url(url).get().build()
        }.mapCatching { body ->
            json.decodeFromString<T>(body)
        }

    suspend fun postForm(baseUrl: String, path: String, params: Map<String, String>): Result<String> {
        val body = FormBody.Builder().apply {
            params.forEach { (key, value) -> add(key, value) }
        }.build()
        return requestWithCsrf(baseUrl, path) { url ->
            Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
        }
    }

    suspend fun postMultipart(
        baseUrl: String,
        path: String,
        parts: Map<String, String>,
    ): Result<String> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                parts.forEach { (key, value) -> addFormDataPart(key, value) }
            }
            .build()
        return requestWithCsrf(baseUrl, path) { url ->
            Request.Builder()
                .url(url)
                .post(body)
                .build()
        }
    }

    suspend fun delete(baseUrl: String, path: String): Result<String> =
        requestWithCsrf(baseUrl, path) { url ->
            Request.Builder().url(url).delete().build()
        }

    suspend inline fun <reified T> get(
        baseUrl: String,
        path: String,
        crossinline parser: (String) -> T,
    ): Result<T> = request(baseUrl, path) { url ->
        Request.Builder().url(url).get().build()
    }.mapCatching(parser)

    suspend fun requestWithCsrf(
        baseUrl: String,
        path: String,
        builder: (String) -> Request,
    ): Result<String> = request(baseUrl, path) { url ->
        val request = builder(url)
        if (csrfToken.isNotBlank()) {
            request.newBuilder()
                .header("X-CSRFToken", csrfToken)
                .build()
        } else {
            request
        }
    }

    suspend fun request(
        baseUrl: String,
        path: String,
        builder: (String) -> Request,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${baseUrl.trim().trimEnd('/')}${path}"
            LogBuffer.append("API $path -> $url")
            val request = builder(url)
            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    LogBuffer.append("API $path unauthorized")
                    AuthManager.onUnauthorized()
                    throw IOException("Unauthorized")
                }
                if (!response.isSuccessful) {
                    LogBuffer.append("API $path failed: HTTP ${response.code}")
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                response.body?.string() ?: ""
            }
        }.onFailure { e ->
            LogBuffer.append("API $path error: ${e.message}")
        }
    }

    @kotlinx.serialization.Serializable
    private data class CsrfResponse(
        @kotlinx.serialization.SerialName("csrf_token")
        val csrfToken: String,
    )

    private class MemoryCookieJar : CookieJar {
        private val storage = mutableMapOf<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            storage[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return storage[url.host] ?: emptyList()
        }
    }
}
