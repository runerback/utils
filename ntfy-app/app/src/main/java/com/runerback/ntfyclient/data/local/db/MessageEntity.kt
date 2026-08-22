package com.runerback.ntfyclient.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AttachmentDownloadState {
    PENDING,
    DOWNLOADED,
    FAILED,
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["topic", "receivedAt"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val topic: String,
    val time: Long,
    val title: String?,
    val message: String?,
    val priority: Int?,
    val tags: String,
    val attachmentUrl: String?,
    val attachmentName: String?,
    val attachmentType: String?,
    val attachmentSize: Long?,
    val attachmentLocalPath: String?,
    val attachmentDownloadState: AttachmentDownloadState = AttachmentDownloadState.PENDING,
    val receivedAt: Long,
)
