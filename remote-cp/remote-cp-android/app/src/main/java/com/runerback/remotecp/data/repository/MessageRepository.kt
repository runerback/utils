package com.runerback.remotecp.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.runerback.remotecp.data.api.MessageApi
import com.runerback.remotecp.data.model.Message
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val prefs: SharedPreferences
) {

    private val gson = Gson()
    private var messageApi: MessageApi = createApi()
    private var socket: Socket = createSocket()

    private fun getBaseUrl(): String {
        return prefs.getString("backend_url", "http://10.0.2.2:5000") ?: "http://10.0.2.2:5000"
    }

    private fun createApi(): MessageApi {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MessageApi::class.java)
    }

    private fun createSocket(): Socket {
        return IO.socket(getBaseUrl())
    }

    fun reconnect() {
        disconnectSocket()
        socket.off()
        messageApi = createApi()
        socket = createSocket()
    }

    suspend fun getMessages(): Result<List<Message>> {
        return try {
            val response = messageApi.getMessages()
            if (response.isSuccessful) {
                val messages = response.body()?.get("messages") ?: emptyList()
                Result.success(messages)
            } else {
                Result.failure(Exception("Failed to fetch messages: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        text: String?,
        deviceType: String,
        clientTimestamp: String,
        images: List<Uri>?, videos: List<Uri>?, files: List<Uri>?,
        context: Context
    ): Result<Message> {
        return try {
            val textBody = text?.toRequestBody("text/plain".toMediaTypeOrNull())
            val deviceTypeBody = deviceType.toRequestBody("text/plain".toMediaTypeOrNull())
            val timestampBody = clientTimestamp.toRequestBody("text/plain".toMediaTypeOrNull())

            val imageParts = images?.map { uriToMultipart(it, "images", context) }
            val videoParts = videos?.map { uriToMultipart(it, "videos", context) }
            val fileParts = files?.map { uriToMultipart(it, "files", context) }

            val response = messageApi.createMessage(
                textBody, deviceTypeBody, timestampBody,
                imageParts, videoParts, fileParts
            )

            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to send message: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun connectSocket(
        onConnect: () -> Unit = {},
        onDisconnect: () -> Unit = {}
    ) {
        if (!socket.connected()) {
            socket.on(Socket.EVENT_CONNECT) {
                onConnect()
            }
            socket.on(Socket.EVENT_DISCONNECT) {
                onDisconnect()
            }
            socket.connect()
        } else {
            onConnect()
        }
    }

    fun disconnectSocket() {
        socket.disconnect()
        socket.off(Socket.EVENT_CONNECT)
        socket.off(Socket.EVENT_DISCONNECT)
    }

    fun observeMessages(): Flow<Message> = callbackFlow {
        val listener = io.socket.emitter.Emitter.Listener { args ->
            val message = gson.fromJson(
                args[0].toString(),
                Message::class.java
            )
            trySend(message)
        }
        socket.on("message:new", listener)
        awaitClose {
            socket.off("message:new", listener)
        }
    }

    private fun uriToMultipart(uri: Uri, partName: String, context: Context): MultipartBody.Part {
        val contentResolver = context.contentResolver
        val fileName = getFileName(uri, contentResolver)
        val tempFile = File(context.cacheDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        val requestBody = tempFile.asRequestBody(
            contentResolver.getType(uri)?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        )
        return MultipartBody.Part.createFormData(partName, fileName, requestBody)
    }

    private fun getFileName(uri: Uri, contentResolver: ContentResolver): String {
        var result = "file"
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index) ?: result
                    }
                }
            }
        } else {
            result = uri.lastPathSegment ?: result
        }
        return result
    }
}
