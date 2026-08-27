package com.runerback.ntfymgr.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "ntfy_mgr_settings")

class SettingsRepository(private val context: Context) {

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:20808"
    }

    val serverUrl: Flow<String> = context.appDataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    suspend fun setServerUrl(url: String) {
        context.appDataStore.edit { preferences ->
            preferences[SERVER_URL] = url.trim().trimEnd('/')
        }
    }
}
