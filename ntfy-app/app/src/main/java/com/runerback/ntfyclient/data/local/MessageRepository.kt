package com.runerback.ntfyclient.data.local

import android.content.Context
import com.runerback.ntfyclient.data.local.db.AppDatabase
import com.runerback.ntfyclient.data.local.db.AttachmentDownloadState
import com.runerback.ntfyclient.data.local.db.MessageEntity
import com.runerback.ntfyclient.data.remote.NtfyMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MessageRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).messageDao()
    private val json = Json { encodeDefaults = true }

    val allMessages: Flow<List<MessageEntity>> = dao.getAllMessages()

    fun messagesForTopic(topic: String): Flow<List<MessageEntity>> = dao.getMessagesByTopic(topic)

    fun messagesForTopicBetween(topic: String, start: Long, end: Long): Flow<List<MessageEntity>> =
        dao.getMessagesForTopicBetween(topic, start, end)

    suspend fun insertFromNtfy(topic: String, message: NtfyMessage) {
        val entity = MessageEntity(
            id = message.id,
            topic = topic,
            time = message.time,
            title = message.title,
            message = message.message,
            priority = message.priority,
            tags = json.encodeToString(message.tags),
            attachmentUrl = message.attachment?.url,
            attachmentName = message.attachment?.name,
            attachmentType = message.attachment?.type,
            attachmentSize = message.attachment?.size,
            attachmentLocalPath = null,
            attachmentDownloadState = if (message.attachment?.url != null) {
                AttachmentDownloadState.PENDING
            } else {
                AttachmentDownloadState.DOWNLOADED
            },
            receivedAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
    }

    suspend fun markAttachmentDownloaded(messageId: String, localPath: String) {
        dao.updateAttachmentState(messageId, localPath, AttachmentDownloadState.DOWNLOADED)
    }

    suspend fun markAttachmentFailed(messageId: String) {
        dao.updateAttachmentState(messageId, null, AttachmentDownloadState.FAILED)
    }

    suspend fun cleanupMessages(topic: String, start: Long, end: Long) {
        val toDelete = dao.getMessagesForTopicBetweenSync(topic, start, end)
        val ids = toDelete.map { it.id }
        if (ids.isEmpty()) return

        toDelete.mapNotNull { it.attachmentLocalPath }.forEach { path ->
            runCatching { File(path).delete() }
        }
        dao.deleteMessagesByIds(ids)
    }

    suspend fun clearTopic(topic: String) {
        val messages = dao.getMessagesByTopicSync(topic)
        messages.mapNotNull { it.attachmentLocalPath }.forEach { path ->
            runCatching { File(path).delete() }
        }
        dao.deleteMessagesForTopic(topic)
    }
}
