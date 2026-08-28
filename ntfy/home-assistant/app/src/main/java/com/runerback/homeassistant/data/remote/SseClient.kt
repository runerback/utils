package com.runerback.homeassistant.data.remote

import com.runerback.homeassistant.data.remote.model.Message
import com.runerback.homeassistant.data.remote.model.MessageEvent
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object SseClient {

    private val client = ApiClient.client.newBuilder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun subscribe(baseUrl: String, topic: String): Flow<Message> = callbackFlow {
        val url = "${baseUrl.trim().trimEnd('/')}/api/messages/stream"
        LogBuffer.append("SSE connecting to $url for topic $topic")
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)

        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        LogBuffer.append("SSE failed: HTTP ${response.code}")
                        close(Exception("SSE failed: ${response.code}"))
                        return@launch
                    }
                    LogBuffer.append("SSE connected for topic $topic")
                    val source = response.body?.source()
                        ?: run {
                            LogBuffer.append("SSE empty body")
                            close(Exception("Empty SSE body"))
                            return@launch
                        }
                    while (isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        try {
                            val event = json.decodeFromString(MessageEvent.serializer(), line)
                            if (event.type == "message" && event.topic == topic) {
                                LogBuffer.append("SSE message on $topic from ${event.sender}")
                                send(
                                    Message(
                                        sender = event.sender,
                                        body = event.body ?: "",
                                        sentAt = event.sentAt ?: java.time.Instant.now().toString(),
                                    )
                                )
                            }
                        } catch (_: SerializationException) {
                            // Ignore malformed lines.
                        }
                    }
                }
            } catch (e: Exception) {
                LogBuffer.append("SSE error for topic $topic: ${e.message}")
                if (isActive) close(e)
            }
        }

        awaitClose {
            LogBuffer.append("SSE closing for topic $topic")
            call.cancel()
        }
    }
}
