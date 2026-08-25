package com.runerback.files.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object AppSettings {

    private const val DEFAULT_SMB_TIMEOUT_MILLIS = 15_000
    private const val DEFAULT_LAN_SHARING_ENABLED = false
    private val SMB_TIMEOUT_MILLIS = intPreferencesKey("smb_timeout_millis")
    private val LAN_SHARING_ENABLED = booleanPreferencesKey("lan_sharing_enabled")

    private lateinit var dataStore: DataStore<Preferences>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _smbTimeoutMillis = MutableStateFlow(DEFAULT_SMB_TIMEOUT_MILLIS.toLong())
    val smbTimeoutMillis: StateFlow<Long> = _smbTimeoutMillis.asStateFlow()

    private val _lanSharingEnabled = MutableStateFlow(DEFAULT_LAN_SHARING_ENABLED)
    val lanSharingEnabled: StateFlow<Boolean> = _lanSharingEnabled.asStateFlow()

    fun init(context: Context) {
        if (::dataStore.isInitialized) return
        dataStore = context.applicationContext.settingsDataStore
        scope.launch {
            dataStore.data.map { prefs ->
                (prefs[SMB_TIMEOUT_MILLIS] ?: DEFAULT_SMB_TIMEOUT_MILLIS).toLong()
            }.collect { timeout ->
                _smbTimeoutMillis.value = timeout
            }
        }
        scope.launch {
            dataStore.data.map { prefs ->
                prefs[LAN_SHARING_ENABLED] ?: DEFAULT_LAN_SHARING_ENABLED
            }.collect { enabled ->
                _lanSharingEnabled.value = enabled
            }
        }
    }

    fun saveSmbTimeoutMillis(timeoutMillis: Long) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[SMB_TIMEOUT_MILLIS] = timeoutMillis.toInt()
            }
        }
    }

    fun saveLanSharingEnabled(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[LAN_SHARING_ENABLED] = enabled
            }
        }
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
