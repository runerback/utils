package com.runerback.ntfyclient.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE topic = :topic ORDER BY receivedAt DESC")
    fun getMessagesByTopic(topic: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE topic = :topic AND receivedAt BETWEEN :start AND :end ORDER BY receivedAt DESC")
    fun getMessagesForTopicBetween(topic: String, start: Long, end: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE topic = :topic AND receivedAt BETWEEN :start AND :end ORDER BY receivedAt DESC")
    suspend fun getMessagesForTopicBetweenSync(topic: String, start: Long, end: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE topic = :topic ORDER BY receivedAt DESC")
    suspend fun getMessagesByTopicSync(topic: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE topic = :topic ORDER BY receivedAt DESC LIMIT 1")
    suspend fun getLatestForTopic(topic: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET attachmentLocalPath = :localPath, attachmentDownloadState = :state WHERE id = :messageId")
    suspend fun updateAttachmentState(
        messageId: String,
        localPath: String?,
        state: AttachmentDownloadState,
    )

    @Query("DELETE FROM messages WHERE topic = :topic")
    suspend fun deleteMessagesForTopic(topic: String)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<String>)
}
