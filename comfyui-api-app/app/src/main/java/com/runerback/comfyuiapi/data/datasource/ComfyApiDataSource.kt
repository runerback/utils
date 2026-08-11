package com.runerback.comfyuiapi.data.datasource

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.runerback.comfyuiapi.data.model.ImageRef
import com.runerback.comfyuiapi.data.model.PromptRequest
import com.runerback.comfyuiapi.data.model.WsMessage
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.ui.components.LogBuffer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

sealed class ComfyEvent {
    data object Connected : ComfyEvent()
    data class Executing(val nodeId: String?) : ComfyEvent()
    data class Progress(val value: Int, val max: Int) : ComfyEvent()
    data class Preview(val bitmap: ImageBitmap) : ComfyEvent()
    data class Executed(val nodeId: String, val output: JsonObject) : ComfyEvent()
    data object Success : ComfyEvent()
    data class Error(val message: String) : ComfyEvent()
    data object Interrupted : ComfyEvent()
}

@Singleton
class ComfyApiDataSource @Inject constructor(
    private val client: HttpClient,
    private val json: kotlinx.serialization.json.Json
) {

    suspend fun queuePrompt(serverUrl: String, workflow: Workflow, clientId: String, promptId: String): Result<Unit> {
        LogBuffer.add("api.queuePrompt: $serverUrl/prompt promptId=$promptId")
        return try {
            val response: HttpResponse = client.post("$serverUrl/prompt") {
                contentType(ContentType.Application.Json)
                setBody(PromptRequest(workflow, clientId, promptId))
            }
            val bodyText = response.bodyAsText()
            LogBuffer.add("api.queuePrompt: status=${response.status} body=$bodyText")
            if (response.status == HttpStatusCode.OK) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.status}: $bodyText"))
            }
        } catch (e: Exception) {
            LogBuffer.add("api.queuePrompt: exception ${e.message}")
            Result.failure(e)
        }
    }

    fun listen(serverUrl: String, clientId: String, promptId: String): Flow<ComfyEvent> = flow {
        val wsUrl = serverUrlToWsUrl(serverUrl)
        LogBuffer.add("api.listen: wsUrl=$wsUrl")
        try {
            client.webSocket("$wsUrl/ws?clientId=$clientId") {
                LogBuffer.add("api.listen: websocket connected")
                emit(ComfyEvent.Connected)

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val raw = frame.data.decodeToString()
                            LogBuffer.add("api.listen: text frame $raw")
                            val message = parseWsMessage(frame.data)
                            val event = messageToEvent(message, promptId)
                            if (event != null) {
                                emit(event)
                                if (event is ComfyEvent.Success || event is ComfyEvent.Error || event is ComfyEvent.Interrupted) {
                                    break
                                }
                            } else {
                                LogBuffer.add("api.listen: ignored type=${message.type} prompt_id=${message.data?.get("prompt_id")?.jsonPrimitive?.content}")
                            }
                        }
                        is Frame.Binary -> {
                            val bytes = frame.readBytes()
                            LogBuffer.add("api.listen: binary frame ${bytes.size} bytes")
                            decodePreview(bytes)?.let { emit(ComfyEvent.Preview(it)) }
                        }
                        else -> Unit
                    }
                }
            }
        } catch (e: Exception) {
            LogBuffer.add("api.listen: exception ${e.message}")
            emit(ComfyEvent.Error(e.message ?: "WebSocket error"))
        }
    }

    suspend fun fetchHistory(serverUrl: String, promptId: String): Result<JsonObject> {
        LogBuffer.add("api.fetchHistory: $serverUrl/history/$promptId")
        return try {
            val response: HttpResponse = client.get("$serverUrl/history/$promptId")
            val bodyText = response.bodyAsText()
            LogBuffer.add("api.fetchHistory: status=${response.status} body=$bodyText")
            if (response.status == HttpStatusCode.OK) {
                Result.success(json.decodeFromString(bodyText))
            } else {
                Result.failure(Exception("HTTP ${response.status}: $bodyText"))
            }
        } catch (e: Exception) {
            LogBuffer.add("api.fetchHistory: exception ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchImage(serverUrl: String, ref: ImageRef): Result<ImageBitmap> {
        LogBuffer.add("api.fetchImage: $serverUrl/view filename=${ref.filename}")
        return try {
            val bytes = client.get("$serverUrl/view") {
                parameter("filename", ref.filename)
                parameter("subfolder", ref.subfolder)
                parameter("type", ref.type)
            }.body<ByteArray>()
            LogBuffer.add("api.fetchImage: ${bytes.size} bytes")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) {
                LogBuffer.add("api.fetchImage: decode failed for ${ref.filename}")
                Result.failure(Exception("Failed to decode image"))
            } else {
                LogBuffer.add("api.fetchImage: decoded ${bitmap.width}x${bitmap.height}")
                Result.success(bitmap.asImageBitmap())
            }
        } catch (e: OutOfMemoryError) {
            LogBuffer.add("api.fetchImage: OOM ${ref.filename}")
            Result.failure(Exception("Image too large"))
        } catch (e: Exception) {
            LogBuffer.add("api.fetchImage: exception ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun uploadImage(
        serverUrl: String,
        filename: String,
        bytes: ByteArray,
        uploadType: String
    ): Result<String> {
        LogBuffer.add("api.uploadImage: $serverUrl/upload/image filename=$filename bytes=${bytes.size}")
        return try {
            val response: HttpResponse = client.post("$serverUrl/upload/image") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("type", uploadType)
                            append("subfolder", "")
                            append("image", bytes, Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                append(HttpHeaders.ContentType, guessMimeType(filename))
                            })
                        }
                    )
                )
            }
            if (response.status == HttpStatusCode.OK) {
                val body: JsonObject = response.body()
                val name = body["name"]?.jsonPrimitive?.content
                LogBuffer.add("api.uploadImage: response name=$name")
                if (name != null) {
                    Result.success(name)
                } else {
                    Result.failure(Exception("No filename in upload response"))
                }
            } else {
                LogBuffer.add("api.uploadImage: status=${response.status}")
                Result.failure(Exception("HTTP ${response.status}"))
            }
        } catch (e: Exception) {
            LogBuffer.add("api.uploadImage: exception ${e.message}")
            Result.failure(e)
        }
    }

    private fun guessMimeType(filename: String): String {
        return when (filename.substringAfterLast(".", "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }
    }

    private fun serverUrlToWsUrl(serverUrl: String): String {
        return when {
            serverUrl.startsWith("http://", ignoreCase = true) ->
                "ws://" + serverUrl.substringAfter("http://")
            serverUrl.startsWith("https://", ignoreCase = true) ->
                "wss://" + serverUrl.substringAfter("https://")
            else -> "ws://$serverUrl"
        }
    }

    private fun parseWsMessage(data: ByteArray): WsMessage {
        val text = data.decodeToString()
        return json.decodeFromString(WsMessage.serializer(), text)
    }

    private fun messageToEvent(message: WsMessage, promptId: String): ComfyEvent? {
        val data = message.data ?: return null
        val msgPromptId = data["prompt_id"]?.jsonPrimitive?.content
        if (msgPromptId != promptId) return null

        return when (message.type) {
            "execution_start" -> ComfyEvent.Executing(null)
            "executing" -> {
                val node = data["node"]?.jsonPrimitive?.content
                if (node == null) null else ComfyEvent.Executing(node)
            }
            "progress" -> {
                val value = data["value"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val max = data["max"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                ComfyEvent.Progress(value, max)
            }
            "executed" -> {
                val node = data["node"]?.jsonPrimitive?.content ?: return null
                val output = data["output"]?.jsonObject ?: return null
                ComfyEvent.Executed(node, output)
            }
            "execution_success" -> ComfyEvent.Success
            "execution_error" -> {
                val msg = data["exception_message"]?.jsonPrimitive?.content ?: "Unknown error"
                ComfyEvent.Error(msg)
            }
            "execution_interrupted" -> ComfyEvent.Interrupted
            else -> null
        }
    }

    private fun decodePreview(bytes: ByteArray): ImageBitmap? {
        if (bytes.size < 8) {
            LogBuffer.add("api.decodePreview: too small ${bytes.size}")
            return null
        }
        val type = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        val format = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        LogBuffer.add("api.decodePreview: type=$type format=$format size=${bytes.size}")
        if (type != 1) return null
        val imageBytes = bytes.copyOfRange(8, bytes.size)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        if (bitmap == null) {
            LogBuffer.add("api.decodePreview: BitmapFactory.decodeByteArray returned null")
            return null
        }
        LogBuffer.add("api.decodePreview: decoded ${bitmap.width}x${bitmap.height}")
        return bitmap.asImageBitmap()
    }
}

fun JsonObject.collectImageRefs(nodeId: String): List<ImageRef> {
    val outputs = this[nodeId]?.jsonObject?.get("outputs")?.jsonObject ?: return emptyList()
    val refs = mutableListOf<ImageRef>()
    for ((_, outputValue) in outputs) {
        val outputObj = outputValue as? kotlinx.serialization.json.JsonObject ?: continue
        val images = outputObj["images"]?.jsonArray ?: continue
        for (image in images) {
            val obj = image as? kotlinx.serialization.json.JsonObject ?: continue
            refs.add(
                ImageRef(
                    filename = obj["filename"]?.jsonPrimitive?.content ?: continue,
                    subfolder = obj["subfolder"]?.jsonPrimitive?.content ?: "",
                    type = obj["type"]?.jsonPrimitive?.content ?: "output"
                )
            )
        }
    }
    LogBuffer.add("api.collectImageRefs: $nodeId -> ${refs.size} refs")
    return refs
}
