package com.runerback.ntfyclient.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.ntfyclient.data.local.MessageRepository
import com.runerback.ntfyclient.data.local.Topic
import com.runerback.ntfyclient.data.local.TopicRepository
import com.runerback.ntfyclient.data.local.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: TopicRepository,
    messageRepository: MessageRepository,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(TopicTab.Receive)
    val selectedTab: StateFlow<TopicTab> = _selectedTab.asStateFlow()

    private val _receiveTopics = MutableStateFlow<List<Topic>>(emptyList())
    val receiveTopics: StateFlow<List<Topic>> = _receiveTopics.asStateFlow()

    private val _sendTopics = MutableStateFlow<List<Topic>>(emptyList())
    val sendTopics: StateFlow<List<Topic>> = _sendTopics.asStateFlow()

    private val _latestMessages = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())
    val latestMessages: StateFlow<Map<String, MessageEntity>> = _latestMessages.asStateFlow()

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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                val context = application.applicationContext
                HomeViewModel(
                    TopicRepository(context),
                    MessageRepository(context),
                )
            }
        }
    }
}
