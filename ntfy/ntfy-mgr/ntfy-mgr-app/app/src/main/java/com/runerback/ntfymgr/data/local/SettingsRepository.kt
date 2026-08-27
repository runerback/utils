package com.runerback.ntfymgr.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runerback.ntfymgr.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val dataStore = context.appDataStore

    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[SERVER_URL_KEY] ?: BuildConfig.DEFAULT_API_URL
    }

    suspend fun setServerUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = url.trim().trimEnd('/')
        }
    }

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
    }
}
