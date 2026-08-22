package com.runerback.ntfyclient.data

import android.content.Context
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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

    fun start() {
        externalScope.launch {
            combine(
                settingsRepository.serverUrl,
                topicRepository.receiveTopics,
            ) { serverUrl, topics -> serverUrl to topics }
                .collect { (serverUrl, topics) ->
                    val enabled = topics.filter { it.enabled }
                    val enabledNames = enabled.map { it.name }.toSet()

                    activeJobs.keys.filter { it !in enabledNames }.forEach { cancelTopic(it) }

                    enabled.forEach { topic ->
                        if (activeJobs[topic.name]?.isActive != true) {
                            activeJobs[topic.name] = launchTopicJob(topic, serverUrl)
                        }
                    }
                }
        }
    }

    fun stop() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    private fun cancelTopic(topicName: String) {
        activeJobs.remove(topicName)?.cancel()
    }

    private fun launchTopicJob(topic: Topic, serverUrl: String): Job = externalScope.launch {
        var delayMs = 1_000L
        while (isActive) {
            try {
                val token = tokenRepository.getToken()
                api.subscribe(serverUrl, topic.name, token)
                    .collect { message ->
                        if (message.event == "message") {
                            messageRepository.insertFromNtfy(topic.name, message)
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
                        delayMs = 1_000L
                    }
            } catch (e: Throwable) {
                if (!isActive) break
                LogBuffer.append("[${topic.name}] connection error: ${e.message}")
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
            }
        }
    }
}
