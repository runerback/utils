package com.runerback.ollamaclient.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {

    private val _serverUrl = MutableStateFlow("http://localhost:11434")
    private val _model = MutableStateFlow("")
    private val _think = MutableStateFlow(false)

    override val serverUrl: Flow<String> = _serverUrl
    override val model: Flow<String> = _model
    override val think: Flow<Boolean> = _think

    override suspend fun setServerUrl(url: String) {
        _serverUrl.value = url
    }

    override suspend fun setModel(model: String) {
        _model.value = model
    }

    override suspend fun setThink(value: Boolean) {
        _think.value = value
    }
}
