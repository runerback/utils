package com.runerback.files.data.datasource

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runerback.files.data.model.FileSource
import com.runerback.files.data.model.TabConfig
import com.runerback.files.ui.components.LogBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {

    private val dataStore = context.dataStore

    val tabs: Flow<List<TabConfig>> = dataStore.data.map { prefs ->
        val saved = prefs[TABS]
        if (saved.isNullOrBlank()) {
            defaultTabs()
        } else {
            try {
                json.decodeFromString(saved)
            } catch (e: Exception) {
                LogBuffer.add("SettingsDataSource.tabs: failed to decode tabs: ${e.message}")
                defaultTabs()
            }
        }
    }

    suspend fun saveTabs(tabs: List<TabConfig>) {
        try {
            tabs.filter { it.source is FileSource.Local }.forEach { tab ->
                val rootUri = (tab.source as FileSource.Local).rootUri
                if (!rootUri.toString().isNullOrEmpty()) {
                    takePersistablePermission(rootUri)
                }
            }
            dataStore.edit { prefs ->
                prefs[TABS] = json.encodeToString(tabs)
            }
        } catch (e: Exception) {
            LogBuffer.add("SettingsDataSource.saveTabs: ${e.stackTraceToString()}")
        }
    }

    suspend fun addTab(tab: TabConfig) {
        try {
            val current = tabs.first() + tab
            saveTabs(current)
        } catch (e: Exception) {
            LogBuffer.add("SettingsDataSource.addTab: ${e.stackTraceToString()}")
        }
    }

    suspend fun removeTab(id: String) {
        try {
            val current = tabs.first().filter { it.id != id }
            saveTabs(current)
        } catch (e: Exception) {
            LogBuffer.add("SettingsDataSource.removeTab: ${e.stackTraceToString()}")
        }
    }

    suspend fun renameTab(id: String, name: String) {
        try {
            val current = tabs.first().map { tab ->
                if (tab.id == id) tab.copy(name = name) else tab
            }
            saveTabs(current)
        } catch (e: Exception) {
            LogBuffer.add("SettingsDataSource.renameTab: ${e.stackTraceToString()}")
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            LogBuffer.add("SettingsDataSource.takePersistableUriPermission: $uri read+write")
        } catch (e: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                LogBuffer.add("SettingsDataSource.takePersistableUriPermission: $uri read-only")
            } catch (e2: SecurityException) {
                LogBuffer.add("SettingsDataSource.takePersistableUriPermission: failed for $uri: ${e2.message}")
            }
        }
    }

    private fun defaultTabs(): List<TabConfig> {
        return listOf(
            TabConfig(
                id = java.util.UUID.randomUUID().toString(),
                name = "Local",
                source = FileSource.Local(Uri.EMPTY)
            )
        )
    }

    companion object {
        private val TABS = stringPreferencesKey("tabs")
    }
}
