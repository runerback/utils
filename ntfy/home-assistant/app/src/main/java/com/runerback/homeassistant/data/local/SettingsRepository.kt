package com.runerback.homeassistant.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    private companion object {
        val MESSAGES_SERVER_URL = stringPreferencesKey("messages.server_url")
        const val DEFAULT_SERVER_URL = "http://localhost"
    }

    val serverUrl: Flow<String> = context.appDataStore.data.map { preferences ->
        preferences[MESSAGES_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    suspend fun setServerUrl(url: String) {
        context.appDataStore.edit { preferences ->
            preferences[MESSAGES_SERVER_URL] = url.trim().trimEnd('/')
        }
    }
}
