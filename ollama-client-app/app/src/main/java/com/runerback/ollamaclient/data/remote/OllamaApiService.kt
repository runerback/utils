package com.runerback.ollamaclient.data.remote

import com.runerback.ollamaclient.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OllamaApiService {

    private val client = OkHttpClient()

    fun chat(
        baseUrl: String,
        model: String,
        messages: List<Message>,
        think: Boolean,
    ): Flow<Message> = flow {
        val requestBody = buildRequestBody(model, messages, think)
        val request = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(requestBody)
            .build()

        var content = ""
        var thinking = ""

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            response.body?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (line.isBlank()) continue

                    val json = JSONObject(line)
                    val message = json.optJSONObject("message") ?: continue
                    val chunkContent = message.optString("content", "")
                    val chunkThinking = message.optString("thinking", "")

                    content += chunkContent
                    thinking += chunkThinking

                    val (cleanContent, extractedThinking) = extractThinking(content)
                    content = cleanContent
                    if (extractedThinking.isNotBlank() && thinking.isBlank()) {
                        thinking = extractedThinking
                    }

                    emit(Message(role = "assistant", content = content, thinking = thinking))

                    if (json.optBoolean("done", false)) break
                }
            }
        }
    }.flowOn(Dispatchers.IO)

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

    private fun extractThinking(content: String): Pair<String, String> {
        val start = content.indexOf("<think>")
        if (start == -1) return content to ""
        val end = content.indexOf("</think>", start)
        if (end == -1) return content to ""

        val thinking = content.substring(start + "<think>".length, end).trim()
        val before = content.substring(0, start)
        val after = content.substring(end + "</think>".length)
        val cleanContent = (before + after).trim()
        return cleanContent to thinking
    }
}
