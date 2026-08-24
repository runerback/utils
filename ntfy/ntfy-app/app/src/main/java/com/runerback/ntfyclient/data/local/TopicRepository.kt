package com.runerback.ntfyclient.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Topic(
    val name: String,
    val enabled: Boolean = false,
    val notify: Boolean = false
)

class TopicRepository(private val context: Context) {

    private companion object {
        val RECEIVE_TOPICS = stringPreferencesKey("receive_topics")
        val SEND_TOPICS = stringPreferencesKey("send_topics")
        val json = Json { encodeDefaults = true }
    }

    val receiveTopics: Flow<List<Topic>> = context.appDataStore.data.map { preferences ->
        preferences[RECEIVE_TOPICS]?.toTopics() ?: emptyList()
    }

    val sendTopics: Flow<List<Topic>> = context.appDataStore.data.map { preferences ->
        preferences[SEND_TOPICS]?.toTopics() ?: emptyList()
    }

    suspend fun addReceiveTopic(topic: Topic) {
        context.appDataStore.edit { preferences ->
            val current = preferences[RECEIVE_TOPICS]?.toTopics() ?: emptyList()
            if (current.none { it.name == topic.name }) {
                preferences[RECEIVE_TOPICS] = (current + topic).toJson()
            }
        }
    }

    suspend fun addSendTopic(topic: Topic) {
        context.appDataStore.edit { preferences ->
            val current = preferences[SEND_TOPICS]?.toTopics() ?: emptyList()
            if (current.none { it.name == topic.name }) {
                preferences[SEND_TOPICS] = (current + topic).toJson()
            }
        }
    }

    suspend fun removeReceiveTopic(topic: Topic) {
        context.appDataStore.edit { preferences ->
            val current = preferences[RECEIVE_TOPICS]?.toTopics() ?: emptyList()
            preferences[RECEIVE_TOPICS] = current.filter { it.name != topic.name }.toJson()
        }
    }

    suspend fun removeSendTopic(topic: Topic) {
        context.appDataStore.edit { preferences ->
            val current = preferences[SEND_TOPICS]?.toTopics() ?: emptyList()
            preferences[SEND_TOPICS] = current.filter { it.name != topic.name }.toJson()
        }
    }

    suspend fun updateReceiveTopic(topic: Topic) {
        context.appDataStore.edit { preferences ->
            val current = preferences[RECEIVE_TOPICS]?.toTopics() ?: emptyList()
            preferences[RECEIVE_TOPICS] = current.map { if (it.name == topic.name) topic else it }.toJson()
        }
    }

    suspend fun updateSendTopic(topic: Topic) {
        context.appDataStore.edit { preferences ->
            val current = preferences[SEND_TOPICS]?.toTopics() ?: emptyList()
            preferences[SEND_TOPICS] = current.map { if (it.name == topic.name) topic else it }.toJson()
        }
    }

    private fun String.toTopics(): List<Topic> =
        json.decodeFromString<List<Topic>>(this)

    private fun List<Topic>.toJson(): String =
        json.encodeToString(this)
}
