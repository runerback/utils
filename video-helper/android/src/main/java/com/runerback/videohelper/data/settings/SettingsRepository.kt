package com.runerback.videohelper.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class ServerSettings(
    val serverUrl: String = "",
    val token: String = "",
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    /** Base URL without trailing slash, e.g. "http://192.168.1.10:31692". */
    val baseUrl: String get() = serverUrl.trim().trimEnd('/')
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.dataStore

    val settings: Flow<ServerSettings> = dataStore.data.map { prefs ->
        ServerSettings(
            serverUrl = prefs[KEY_SERVER_URL].orEmpty(),
            token = prefs[KEY_TOKEN].orEmpty(),
        )
    }

    suspend fun save(settings: ServerSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = settings.serverUrl.trim()
            prefs[KEY_TOKEN] = settings.token.trim()
        }
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_TOKEN = stringPreferencesKey("token")
    }
}
