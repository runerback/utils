package com.runerback.ntfyclient.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.runerback.ntfyclient.data.local.MessageRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class AttachmentDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val messageRepository = MessageRepository(applicationContext)
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: "attachment"

        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    messageRepository.markAttachmentFailed(messageId)
                    return Result.retry()
                }

                val body = response.body ?: run {
                    messageRepository.markAttachmentFailed(messageId)
                    return Result.failure()
                }

                val dir = applicationContext.getExternalFilesDir(null)
                    ?: applicationContext.filesDir
                val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val file = File(dir, "${messageId}_$safeName")
                file.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }

                messageRepository.markAttachmentDownloaded(messageId, file.absolutePath)
                Result.success()
            }
        } catch (e: IOException) {
            messageRepository.markAttachmentFailed(messageId)
            Result.retry()
        }
    }

    companion object {
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_URL = "url"
        const val KEY_NAME = "name"
    }
}
