package com.runerback.whoiscalling.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class LookupSettings(
    val enabled: Boolean = true,
    val apiUrlTemplate: String = "",
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val blockSpam: Boolean = false,
    val notifyOnLookupFailure: Boolean = true,
) {
    companion object {
        const val NUMBER_PLACEHOLDER = "{number}"
        const val DEFAULT_TIMEOUT_MILLIS = 3000L
        const val MIN_TIMEOUT_MILLIS = 500L
        const val MAX_TIMEOUT_MILLIS = 4000L
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val API_URL_TEMPLATE = stringPreferencesKey("api_url_template")
        val TIMEOUT_MILLIS = longPreferencesKey("timeout_millis")
        val BLOCK_SPAM = booleanPreferencesKey("block_spam")
        val NOTIFY_ON_LOOKUP_FAILURE = booleanPreferencesKey("notify_on_lookup_failure")
    }

    val settings: Flow<LookupSettings> = dataStore.data.map { prefs ->
        LookupSettings(
            enabled = prefs[Keys.ENABLED] ?: true,
            apiUrlTemplate = prefs[Keys.API_URL_TEMPLATE] ?: "",
            timeoutMillis = (prefs[Keys.TIMEOUT_MILLIS] ?: LookupSettings.DEFAULT_TIMEOUT_MILLIS)
                .coerceIn(LookupSettings.MIN_TIMEOUT_MILLIS, LookupSettings.MAX_TIMEOUT_MILLIS),
            blockSpam = prefs[Keys.BLOCK_SPAM] ?: false,
            notifyOnLookupFailure = prefs[Keys.NOTIFY_ON_LOOKUP_FAILURE] ?: true,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setApiUrlTemplate(template: String) {
        dataStore.edit { it[Keys.API_URL_TEMPLATE] = template.trim() }
    }

    suspend fun setTimeoutMillis(timeoutMillis: Long) {
        dataStore.edit {
            it[Keys.TIMEOUT_MILLIS] = timeoutMillis.coerceIn(
                LookupSettings.MIN_TIMEOUT_MILLIS,
                LookupSettings.MAX_TIMEOUT_MILLIS,
            )
        }
    }

    suspend fun setBlockSpam(blockSpam: Boolean) {
        dataStore.edit { it[Keys.BLOCK_SPAM] = blockSpam }
    }

    suspend fun setNotifyOnLookupFailure(notify: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_ON_LOOKUP_FAILURE] = notify }
    }
}
