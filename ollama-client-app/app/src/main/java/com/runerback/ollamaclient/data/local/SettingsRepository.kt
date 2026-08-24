package com.runerback.ollamaclient.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val THINK = booleanPreferencesKey("think")
        const val DEFAULT_SERVER_URL = "http://localhost:11434"
    }

    val serverUrl: Flow<String> = context.appDataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val think: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[THINK] ?: false
    }

    suspend fun setServerUrl(url: String) {
        context.appDataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }

    suspend fun setThink(value: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[THINK] = value
        }
    }
}
