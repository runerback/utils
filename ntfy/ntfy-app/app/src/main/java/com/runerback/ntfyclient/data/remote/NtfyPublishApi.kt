package com.runerback.ntfyclient.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

class NtfyPublishApi {

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun publish(
        serverUrl: String,
        topic: String,
        token: String?,
        message: String,
    ): Result<NtfyMessage> = withContext(Dispatchers.IO) {
        val baseUrl = serverUrl.trim().trimEnd('/')
        val url = "$baseUrl/$topic".toHttpUrlOrNull()
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid server URL or topic: $baseUrl/$topic")
            )

        val body = message.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Publish failed: ${response.code} ${response.message}")
                }
                val responseBody = response.body?.string()
                    ?: throw IOException("Empty response body")
                json.decodeFromString(NtfyMessage.serializer(), responseBody)
            }
        }
    }
}
