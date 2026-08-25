package com.runerback.ntfyclient.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val DOWNLOAD_ATTACHMENTS_UNMETERED_ONLY = booleanPreferencesKey("download_attachments_unmetered_only")
        val BACKGROUND_LISTENING_ENABLED = booleanPreferencesKey("background_listening_enabled")
        const val DEFAULT_SERVER_URL = "https://ntfy.sh"
    }

    val serverUrl: Flow<String> = context.appDataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val downloadAttachmentsUnmeteredOnly: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[DOWNLOAD_ATTACHMENTS_UNMETERED_ONLY] ?: true
    }

    val backgroundListeningEnabled: Flow<Boolean> = context.appDataStore.data.map { preferences ->
        preferences[BACKGROUND_LISTENING_ENABLED] ?: false
    }

    suspend fun setServerUrl(url: String) {
        context.appDataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }

    suspend fun setDownloadAttachmentsUnmeteredOnly(value: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[DOWNLOAD_ATTACHMENTS_UNMETERED_ONLY] = value
        }
    }

    suspend fun setBackgroundListeningEnabled(value: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[BACKGROUND_LISTENING_ENABLED] = value
        }
    }
}
