package com.runerback.homeassistant.ui.messages

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.runerback.homeassistant.data.local.SettingsRepository
import com.runerback.homeassistant.data.remote.ApiClient
import com.runerback.homeassistant.data.remote.model.Topic
import com.runerback.homeassistant.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MessagesViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _topics = MutableStateFlow<List<Topic>>(emptyList())
    val topics: StateFlow<List<Topic>> = _topics.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.get<List<Topic>>(baseUrl, "/api/topics")
                .onSuccess {
                    LogBuffer.append("Loaded ${it.size} topics")
                    _topics.value = it
                }
                .onFailure {
                    LogBuffer.append("Failed to load topics: ${it.message}")
                    _error.value = it.message
                }
            _loading.value = false
        }
    }

    fun addTopic(name: String) {
        viewModelScope.launch {
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.postForm(baseUrl, "/api/topics", mapOf("name" to name.trim()))
                .onSuccess {
                    LogBuffer.append("Added topic $name")
                    load()
                }
                .onFailure {
                    LogBuffer.append("Failed to add topic $name: ${it.message}")
                    _error.value = it.message
                }
        }
    }

    fun deleteTopic(id: Long) {
        viewModelScope.launch {
            _error.value = null
            val baseUrl = settingsRepository.serverUrl.first()
            ApiClient.delete(baseUrl, "/api/topics/$id")
                .onSuccess {
                    LogBuffer.append("Deleted topic $id")
                    load()
                }
                .onFailure {
                    LogBuffer.append("Failed to delete topic $id: ${it.message}")
                    _error.value = it.message
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("Application not available")
                MessagesViewModel(SettingsRepository(application.applicationContext))
            }
        }
    }
}
