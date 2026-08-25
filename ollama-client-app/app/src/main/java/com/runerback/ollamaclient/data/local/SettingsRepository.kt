package com.runerback.ollamaclient.data.local

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val serverUrl: Flow<String>
    val model: Flow<String>
    val think: Flow<Boolean>

    suspend fun setServerUrl(url: String)
    suspend fun setModel(model: String)
    suspend fun setThink(value: Boolean)
}
