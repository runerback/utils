package com.runerback.ollamaclient.data.remote

import com.runerback.ollamaclient.data.model.Message
import com.runerback.ollamaclient.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

open class OllamaApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    open fun chat(
        baseUrl: String,
        model: String,
        messages: List<Message>,
        think: Boolean,
    ): Flow<Message> = flow {
        val url = "$baseUrl/api/chat"
        val requestBody = buildRequestBody(model, messages, think)
        LogBuffer.append("POST $url model=$model messages=${messages.size}")

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        var rawContent = ""
        var thinkingFromField = ""

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string()?.take(500) ?: ""
                    LogBuffer.append("HTTP error ${response.code} at $url body=$body")
                    throw IllegalStateException("HTTP ${response.code} for $url (model=$model)")
                }
                response.body?.source()?.use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (line.isBlank()) continue

                        val json = JSONObject(line)
                        val message = json.optJSONObject("message") ?: continue
                        val chunkContent = message.optString("content", "")
                        val chunkThinking = message.optString("thinking", "")

                        rawContent += chunkContent
                        thinkingFromField += chunkThinking

                        val (cleanContent, extractedThinking) = extractThinkingStreaming(rawContent)
                        val displayThinking = if (thinkingFromField.isNotBlank()) {
                            thinkingFromField
                        } else {
                            extractedThinking
                        }

                        emit(Message(role = "assistant", content = cleanContent, thinking = displayThinking))

                        if (json.optBoolean("done", false)) break
                    }
                }
            }
            LogBuffer.append("Chat stream completed for model=$model")
        } catch (e: IOException) {
            val cause = describeNetworkError(e, url)
            LogBuffer.append("Chat network error: $cause")
            throw IllegalStateException(cause)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun listModels(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/tags"
        LogBuffer.append("GET $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string()?.take(500) ?: ""
                    LogBuffer.append("HTTP error ${response.code} at $url body=$body")
                    throw IllegalStateException("HTTP ${response.code} for $url")
                }
                val body = response.body?.string() ?: "{}"
                LogBuffer.append("Models response: ${body.take(200)}")
                val json = JSONObject(body)
                val modelsArray = json.optJSONArray("models") ?: return@withContext emptyList()
                return@withContext (0 until modelsArray.length()).mapNotNull { index ->
                    modelsArray.optJSONObject(index)?.optString("name")
                }
            }
        } catch (e: IOException) {
            val cause = describeNetworkError(e, url)
            LogBuffer.append("List models network error: $cause")
            throw IllegalStateException(cause)
        }
    }

    private fun describeNetworkError(e: IOException, url: String): String {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "unknown error"
        return when (e) {
            is ConnectException -> "Cannot connect to Ollama at $url ($detail). Is the server running?"
            is SocketTimeoutException -> "Connection to $url timed out ($detail)."
            else -> "Cannot reach Ollama at $url: $detail"
        }
    }

    private fun buildRequestBody(
        model: String,
        messages: List<Message>,
        think: Boolean,
    ) = JSONObject().apply {
        put("model", model)
        put("stream", true)
        put("think", think)
        put("messages", JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        })
    }.toString().toRequestBody("application/json".toMediaType())

    private fun extractThinkingStreaming(content: String): Pair<String, String> {
        val start = content.indexOf("<think>")
        if (start == -1) return content to ""

        val thinkStart = start + "<think>".length
        val end = content.indexOf("</think>", thinkStart)

        val thinking = if (end == -1) {
            content.substring(thinkStart)
        } else {
            content.substring(thinkStart, end)
        }.trim()

        val before = content.substring(0, start)
        val after = if (end == -1) "" else content.substring(end + "</think>".length)
        val cleanContent = (before + after).trim()
        return cleanContent to thinking
    }
}
