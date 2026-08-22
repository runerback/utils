package com.runerback.ntfyclient.data

import android.content.Context
import android.util.Log
import com.runerback.ntfyclient.data.local.MessageRepository
import com.runerback.ntfyclient.data.local.SettingsRepository
import com.runerback.ntfyclient.data.local.TokenRepository
import com.runerback.ntfyclient.data.local.Topic
import com.runerback.ntfyclient.data.local.TopicRepository
import com.runerback.ntfyclient.data.remote.NtfySubscriptionApi
import com.runerback.ntfyclient.util.LogBuffer
import com.runerback.ntfyclient.util.NotificationHelper
import com.runerback.ntfyclient.work.AttachmentScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}

class SubscriptionManager(
    context: Context,
    private val externalScope: CoroutineScope,
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    private val topicRepository: TopicRepository = TopicRepository(context),
    private val tokenRepository: TokenRepository = TokenRepository(context),
    private val messageRepository: MessageRepository = MessageRepository(context),
    private val api: NtfySubscriptionApi = NtfySubscriptionApi(),
) {

    private val appContext = context.applicationContext
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    fun start() {
        Log.d(TAG, "Starting subscription manager")
        externalScope.launch {
            combine(
                settingsRepository.serverUrl,
                topicRepository.receiveTopics,
            ) { serverUrl, topics -> serverUrl to topics }
                .collect { (serverUrl, topics) ->
                    val enabled = topics.filter { it.enabled }
                    val enabledNames = enabled.map { it.name }.toSet()

                    activeJobs.keys.filter { it !in enabledNames }.forEach { cancelTopic(it) }

                    _connectionStates.update { states ->
                        states.mapValues { (name, state) ->
                            if (name in enabledNames) state else ConnectionState.DISCONNECTED
                        }
                    }

                    enabled.forEach { topic ->
                        if (activeJobs[topic.name]?.isActive != true) {
                            activeJobs[topic.name] = launchTopicJob(topic, serverUrl)
                        }
                    }
                }
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping subscription manager")
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _connectionStates.update { emptyMap() }
    }

    private fun cancelTopic(topicName: String) {
        Log.d(TAG, "Cancelling topic: $topicName")
        activeJobs.remove(topicName)?.cancel()
    }

    private fun launchTopicJob(topic: Topic, serverUrl: String): Job = externalScope.launch {
        var delayMs = 1_000L
        while (isActive) {
            try {
                _connectionStates.update { it + (topic.name to ConnectionState.CONNECTING) }
                Log.d(TAG, "[${topic.name}] connecting to $serverUrl...")
                LogBuffer.append("[${topic.name}] connecting to $serverUrl...")
                val token = tokenRepository.getToken()
                api.subscribe(serverUrl, topic.name, token) {
                    _connectionStates.update { it + (topic.name to ConnectionState.CONNECTED) }
                    Log.d(TAG, "[${topic.name}] connected")
                    LogBuffer.append("[${topic.name}] connected")
                    delayMs = 1_000L
                }.collect { message ->
                    if (message.event == "message") {
                        messageRepository.insertFromNtfy(topic.name, message)
                        Log.d(TAG, "[${topic.name}] message: ${message.title ?: message.topic}: ${message.message}")
                        LogBuffer.append("[${topic.name}] ${message.title ?: message.topic}: ${message.message}")
                        if (topic.notify) {
                            NotificationHelper.notify(appContext, topic.name, message)
                        }
                        message.attachment?.url?.let { url ->
                            val unmeteredOnly = settingsRepository.downloadAttachmentsUnmeteredOnly.first()
                            AttachmentScheduler.schedule(
                                appContext,
                                message.id,
                                url,
                                message.attachment.name,
                                unmeteredOnly,
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                if (!isActive) break
                _connectionStates.update { it + (topic.name to ConnectionState.ERROR) }
                Log.e(TAG, "[${topic.name}] connection error", e)
                LogBuffer.append("[${topic.name}] connection error: ${e.message}")
                delay(delayMs)
                Log.d(TAG, "[${topic.name}] reconnecting in ${delayMs / 1000}s...")
                LogBuffer.append("[${topic.name}] reconnecting in ${delayMs / 1000}s...")
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
            }
        }
        _connectionStates.update { it + (topic.name to ConnectionState.DISCONNECTED) }
        Log.d(TAG, "[${topic.name}] disconnected")
    }

    companion object {
        private const val TAG = "SubscriptionManager"
    }
}
