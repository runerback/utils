package com.runerback.comfyuiapi.data.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {

    private val dataStore = context.dataStore

    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[SERVER_URL] ?: DEFAULT_URL
    }

    val clientId: Flow<String> = dataStore.data.map { prefs ->
        prefs[CLIENT_ID] ?: UUID.randomUUID().toString()
    }

    val serverUrlHistory: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[SERVER_URL_HISTORY]?.let { json.decodeFromString(it) } ?: emptyList()
    }

    val generationTimeoutMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[GENERATION_TIMEOUT_MS] ?: DEFAULT_TIMEOUT_MS
    }

    suspend fun setServerUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = url
        }
    }

    suspend fun addServerUrlToHistory(url: String) {
        if (url.isBlank()) return
        val current = serverUrlHistory.first().toMutableList()
        current.remove(url)
        current.add(0, url)
        dataStore.edit { prefs ->
            prefs[SERVER_URL_HISTORY] = json.encodeToString(current.take(MAX_HISTORY))
        }
    }

    suspend fun setGenerationTimeoutMs(ms: Long) {
        dataStore.edit { prefs ->
            prefs[GENERATION_TIMEOUT_MS] = ms
        }
    }

    suspend fun ensureClientId(): String {
        var existing: String? = null
        dataStore.edit { prefs ->
            existing = prefs[CLIENT_ID]
            if (existing == null) {
                existing = UUID.randomUUID().toString()
                prefs[CLIENT_ID] = existing!!
            }
        }
        return existing!!
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val CLIENT_ID = stringPreferencesKey("client_id")
        private val SERVER_URL_HISTORY = stringPreferencesKey("server_url_history")
        private val GENERATION_TIMEOUT_MS = longPreferencesKey("generation_timeout_ms")
        private const val DEFAULT_URL = "http://10.0.2.2:8188"
        private const val MAX_HISTORY = 10
        private const val DEFAULT_TIMEOUT_MS = 30000L
    }
}
