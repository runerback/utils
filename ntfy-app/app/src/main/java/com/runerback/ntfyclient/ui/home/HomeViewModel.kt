package com.runerback.ntfyclient.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ntfyclient.data.SubscriptionManager
import com.runerback.ntfyclient.data.local.MessageRepository
import com.runerback.ntfyclient.data.local.Topic
import com.runerback.ntfyclient.data.local.TopicRepository
import com.runerback.ntfyclient.data.local.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: TopicRepository,
    private val messageRepository: MessageRepository,
    subscriptionManager: SubscriptionManager,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(TopicTab.Receive)
    val selectedTab: StateFlow<TopicTab> = _selectedTab.asStateFlow()

    private val _receiveTopics = MutableStateFlow<List<Topic>>(emptyList())
    val receiveTopics: StateFlow<List<Topic>> = _receiveTopics.asStateFlow()

    private val _sendTopics = MutableStateFlow<List<Topic>>(emptyList())
    val sendTopics: StateFlow<List<Topic>> = _sendTopics.asStateFlow()

    private val _latestMessages = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())
    val latestMessages: StateFlow<Map<String, MessageEntity>> = _latestMessages.asStateFlow()

    val connectionStates: StateFlow<Map<String, com.runerback.ntfyclient.data.ConnectionState>> =
        subscriptionManager.connectionStates

    private val _historyTopic = MutableStateFlow<String?>(null)
    val historyTopic: StateFlow<String?> = _historyTopic.asStateFlow()

    val historyMessages: StateFlow<List<MessageEntity>> = _historyTopic
        .flatMapLatest { topic ->
            if (topic == null) flowOf(emptyList()) else messageRepository.messagesForTopic(topic)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repository.receiveTopics.collect { _receiveTopics.value = it }
        }
        viewModelScope.launch {
            repository.sendTopics.collect { _sendTopics.value = it }
        }
        viewModelScope.launch {
            messageRepository.allMessages
                .map { messages -> messages.groupBy { it.topic }.mapValues { it.value.first() } }
                .collect { _latestMessages.value = it }
        }
    }

    fun selectTab(tab: TopicTab) {
        _selectedTab.value = tab
    }

    fun addTopic(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val topic = Topic(trimmed)
        viewModelScope.launch {
            when (_selectedTab.value) {
                TopicTab.Receive -> repository.addReceiveTopic(topic)
                TopicTab.Send -> repository.addSendTopic(topic)
            }
        }
    }

    fun removeTopic(topic: Topic) {
        viewModelScope.launch {
            when (_selectedTab.value) {
                TopicTab.Receive -> repository.removeReceiveTopic(topic)
                TopicTab.Send -> repository.removeSendTopic(topic)
            }
        }
    }

    fun updateTopic(topic: Topic) {
        viewModelScope.launch {
            when (_selectedTab.value) {
                TopicTab.Receive -> repository.updateReceiveTopic(topic)
                TopicTab.Send -> repository.updateSendTopic(topic)
            }
        }
    }

    fun showHistory(topicName: String) {
        _historyTopic.value = topicName
    }

    fun clearHistory() {
        _historyTopic.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                val context = application.applicationContext
                val ntfyApplication = application as com.runerback.ntfyclient.NtfyApplication
                HomeViewModel(
                    TopicRepository(context),
                    MessageRepository(context),
                    ntfyApplication.subscriptionManager,
                )
            }
        }
    }
}
