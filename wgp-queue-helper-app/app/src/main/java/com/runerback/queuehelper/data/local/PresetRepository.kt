package com.runerback.queuehelper.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runerback.queuehelper.data.model.Preset
import com.runerback.queuehelper.ui.components.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

private val Context.presetDataStore: DataStore<Preferences> by preferencesDataStore(name = "presets")

class PresetRepository(private val context: Context) {

    companion object {
        private const val TAG = "PresetRepository"
    }

    private val dataStore = context.presetDataStore
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val presetsKey = stringPreferencesKey("preset_list")
    private val nextIdKey = intPreferencesKey("next_id")

    private fun presetsDir(): File = File(context.filesDir, "presets").apply { mkdirs() }

    private fun payloadFile(id: Int): File = File(presetsDir(), "preset_$id.json")

    suspend fun loadPresets(): List<Preset> = withContext(Dispatchers.IO) {
        Log.d(TAG, "loadPresets() started")
        LogBuffer.add("PresetRepository.loadPresets() started")
        runCatching {
            val prefs = dataStore.data.first()
            val listJson = prefs[presetsKey] ?: "[]"
            val summaries = json.decodeFromString<List<PresetSummary>>(listJson)
            Log.d(TAG, "loadPresets() found ${summaries.size} preset summaries")
            LogBuffer.add("PresetRepository.loadPresets() found ${summaries.size} preset summaries")
            summaries.mapNotNull { summary ->
                val payloadFile = payloadFile(summary.id)
                if (!payloadFile.exists()) {
                    Log.w(TAG, "loadPresets() payload file missing for preset id=${summary.id}, name=${summary.name}")
                    LogBuffer.add("PresetRepository.loadPresets() payload file missing for preset id=${summary.id}, name=${summary.name}")
                    return@mapNotNull null
                }
                val payload = json.decodeFromString<JsonObject>(payloadFile.readText())
                Preset(
                    id = summary.id,
                    name = summary.name,
                    modelType = summary.modelType,
                    createdAt = summary.createdAt,
                    payload = payload
                )
            }.sortedBy { it.createdAt }
        }.getOrElse {
            Log.e(TAG, "loadPresets() failed", it)
            LogBuffer.add("PresetRepository.loadPresets: ${it.stackTraceToString()}")
            emptyList()
        }.also {
            Log.d(TAG, "loadPresets() returning ${it.size} presets")
            LogBuffer.add("PresetRepository.loadPresets() returning ${it.size} presets")
        }
    }

    suspend fun loadPreset(id: Int): Preset? = withContext(Dispatchers.IO) {
        Log.d(TAG, "loadPreset($id) started")
        LogBuffer.add("PresetRepository.loadPreset($id) started")
        runCatching {
            loadPresets().find { it.id == id }
        }.getOrElse {
            Log.e(TAG, "loadPreset($id) failed", it)
            LogBuffer.add("PresetRepository.loadPreset($id): ${it.stackTraceToString()}")
            null
        }.also {
            Log.d(TAG, "loadPreset($id) returned ${it?.name ?: "null"}")
            LogBuffer.add("PresetRepository.loadPreset($id) returned ${it?.name ?: "null"}")
        }
    }

    suspend fun savePreset(preset: Preset): Preset = withContext(Dispatchers.IO) {
        runCatching {
            payloadFile(preset.id).writeText(json.encodeToString(preset.payload))

            dataStore.edit { prefs ->
                val existing = json.decodeFromString<List<PresetSummary>>(prefs[presetsKey] ?: "[]")
                val updated = existing.filter { it.id != preset.id } + PresetSummary(
                    id = preset.id,
                    name = preset.name,
                    modelType = preset.modelType,
                    createdAt = preset.createdAt
                )
                prefs[presetsKey] = json.encodeToString(updated)
            }
            preset
        }.getOrElse {
            LogBuffer.add("PresetRepository.savePreset(${preset.id}): ${it.stackTraceToString()}")
            preset
        }
    }

    suspend fun deletePreset(id: Int) = withContext(Dispatchers.IO) {
        runCatching {
            payloadFile(id).delete()
            dataStore.edit { prefs ->
                val existing = json.decodeFromString<List<PresetSummary>>(prefs[presetsKey] ?: "[]")
                prefs[presetsKey] = json.encodeToString(existing.filter { it.id != id })
            }
        }.getOrElse {
            LogBuffer.add("PresetRepository.deletePreset($id): ${it.stackTraceToString()}")
        }
    }

    suspend fun renumberPresets() = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first()
            val summaries = json.decodeFromString<List<PresetSummary>>(prefs[presetsKey] ?: "[]")
            val sorted = summaries.sortedBy { it.createdAt }

            val newSummaries = sorted.mapIndexed { index, summary ->
                val newId = index + 1
                val oldId = summary.id
                if (oldId != newId) {
                    val oldFile = payloadFile(oldId)
                    if (oldFile.exists()) {
                        val payload = json.decodeFromString<JsonObject>(oldFile.readText())
                        val updatedPayload = updatePayloadId(payload, newId)
                        payloadFile(newId).writeText(json.encodeToString(updatedPayload))
                        oldFile.delete()
                    }
                }
                summary.copy(id = newId)
            }

            dataStore.edit { prefs ->
                prefs[presetsKey] = json.encodeToString(newSummaries)
                prefs[nextIdKey] = newSummaries.size + 1
            }
        }.getOrElse {
            LogBuffer.add("PresetRepository.renumberPresets: ${it.stackTraceToString()}")
        }
    }

    private fun updatePayloadId(payload: JsonObject, newId: Int): JsonObject {
        val updated = payload.toMutableMap()
        updated["id"] = JsonPrimitive(newId)
        val params = payload["params"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        params["id"] = JsonPrimitive(newId)
        updated["params"] = JsonObject(params)
        return JsonObject(updated)
    }

    suspend fun nextId(): Int = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            val current = prefs[nextIdKey] ?: 1
            prefs[nextIdKey] = current + 1
        }
        dataStore.data.first()[nextIdKey]?.minus(1) ?: 1
    }

    @kotlinx.serialization.Serializable
    private data class PresetSummary(
        val id: Int,
        val name: String,
        val modelType: String,
        val createdAt: Long
    )
}
