package com.runerback.ollamaclient.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsDataStore(private val context: Context) : SettingsRepository {

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val MODEL = stringPreferencesKey("model")
        val THINK = booleanPreferencesKey("think")
        const val DEFAULT_SERVER_URL = "http://localhost:11434"
    }

    override val serverUrl: Flow<String> = context.appDataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    override val model: Flow<String> = context.appDataStore.data.map { preferences ->
        preferences[MODEL] ?: ""
    }

    override val think: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[THINK] ?: false
    }

    override suspend fun setServerUrl(url: String) {
        context.appDataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }

    override suspend fun setModel(model: String) {
        context.appDataStore.edit { preferences ->
            preferences[MODEL] = model
        }
    }

    override suspend fun setThink(value: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[THINK] = value
        }
    }
}
