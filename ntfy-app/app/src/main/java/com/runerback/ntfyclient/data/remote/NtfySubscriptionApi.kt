package com.runerback.ntfyclient.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

class NtfySubscriptionApi {

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun subscribe(
        serverUrl: String,
        topic: String,
        token: String?,
    ): Flow<NtfyMessage> = channelFlow {
        val baseUrl = serverUrl.trim().trimEnd('/')
        val url = "$baseUrl/$topic/json".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid server URL or topic: $baseUrl/$topic")

        val request = Request.Builder()
            .url(url)
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        val call = client.newCall(request)

        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        close(IOException("Subscription failed: ${response.code} ${response.message}"))
                        return@launch
                    }

                    val source = response.body?.source()
                        ?: run {
                            close(IOException("Empty response body"))
                            return@launch
                        }

                    while (isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        try {
                            val message = json.decodeFromString(NtfyMessage.serializer(), line)
                            send(message)
                        } catch (e: SerializationException) {
                            // Ignore malformed lines; the server may send events we do not model.
                        }
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    close(e)
                }
            }
        }

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)
}
