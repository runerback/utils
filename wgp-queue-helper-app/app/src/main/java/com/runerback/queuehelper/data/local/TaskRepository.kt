package com.runerback.queuehelper.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runerback.queuehelper.data.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

private val Context.taskDataStore: DataStore<Preferences> by preferencesDataStore(name = "tasks")

class TaskRepository(private val context: Context) {

    private val dataStore = context.taskDataStore
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val tasksKey = stringPreferencesKey("task_list")
    private val nextIdKey = intPreferencesKey("next_id")

    private fun tasksDir(): File = File(context.filesDir, "tasks").apply { mkdirs() }

    private fun payloadFile(id: Int): File = File(tasksDir(), "task_$id.json")

    suspend fun loadTasks(): List<Task> = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val listJson = prefs[tasksKey] ?: "[]"
        val summaries = json.decodeFromString<List<TaskSummary>>(listJson)
        summaries.mapNotNull { summary ->
            val payloadFile = payloadFile(summary.id)
            if (!payloadFile.exists()) return@mapNotNull null
            val payload = json.decodeFromString<JsonObject>(payloadFile.readText())
            Task(
                id = summary.id,
                name = summary.name,
                modelType = summary.modelType,
                createdAt = summary.createdAt,
                payload = payload
            )
        }.sortedBy { it.createdAt }
    }

    suspend fun loadTask(id: Int): Task? = withContext(Dispatchers.IO) {
        loadTasks().find { it.id == id }
    }

    suspend fun saveTask(task: Task): Task = withContext(Dispatchers.IO) {
        payloadFile(task.id).writeText(json.encodeToString(task.payload))

        dataStore.edit { prefs ->
            val existing = json.decodeFromString<List<TaskSummary>>(prefs[tasksKey] ?: "[]")
            val updated = existing.filter { it.id != task.id } + TaskSummary(
                id = task.id,
                name = task.name,
                modelType = task.modelType,
                createdAt = task.createdAt
            )
            prefs[tasksKey] = json.encodeToString(updated)
        }
        task
    }

    suspend fun deleteTask(id: Int) = withContext(Dispatchers.IO) {
        payloadFile(id).delete()
        dataStore.edit { prefs ->
            val existing = json.decodeFromString<List<TaskSummary>>(prefs[tasksKey] ?: "[]")
            prefs[tasksKey] = json.encodeToString(existing.filter { it.id != id })
        }
    }

    suspend fun renumberTasks() = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val summaries = json.decodeFromString<List<TaskSummary>>(prefs[tasksKey] ?: "[]")
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
            prefs[tasksKey] = json.encodeToString(newSummaries)
            prefs[nextIdKey] = newSummaries.size + 1
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
    private data class TaskSummary(
        val id: Int,
        val name: String,
        val modelType: String,
        val createdAt: Long
    )
}
