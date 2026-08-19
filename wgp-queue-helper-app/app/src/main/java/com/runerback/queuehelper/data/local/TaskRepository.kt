package com.runerback.queuehelper.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runerback.queuehelper.data.model.QueueJob
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

private val Context.queueJobDataStore: DataStore<Preferences> by preferencesDataStore(name = "queue_jobs")

class QueueJobRepository(private val context: Context) {

    private val dataStore = context.queueJobDataStore
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val jobsKey = stringPreferencesKey("job_list")
    private val nextIdKey = intPreferencesKey("next_id")

    private fun jobsDir(): File = File(context.filesDir, "queue_jobs").apply { mkdirs() }

    private fun payloadFile(id: Int): File = File(jobsDir(), "job_$id.json")

    suspend fun loadJobs(presetId: Int): List<QueueJob> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first()
            val listJson = prefs[jobsKey] ?: "[]"
            val summaries = json.decodeFromString<List<JobSummary>>(listJson)
            summaries
                .filter { it.presetId == presetId }
                .mapNotNull { summary ->
                    val payloadFile = payloadFile(summary.id)
                    if (!payloadFile.exists()) return@mapNotNull null
                    val payload = json.decodeFromString<JsonObject>(payloadFile.readText())
                    QueueJob(
                        id = summary.id,
                        presetId = summary.presetId,
                        createdAt = summary.createdAt,
                        payload = payload
                    )
                }
                .sortedBy { it.createdAt }
        }.getOrElse {
            LogBuffer.add("QueueJobRepository.loadJobs($presetId): ${it.stackTraceToString()}")
            emptyList()
        }
    }

    suspend fun loadJob(id: Int): QueueJob? = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first()
            val summaries = json.decodeFromString<List<JobSummary>>(prefs[jobsKey] ?: "[]")
            summaries.find { it.id == id }?.let { summary ->
                val payloadFile = payloadFile(summary.id)
                if (!payloadFile.exists()) return@let null
                val payload = json.decodeFromString<JsonObject>(payloadFile.readText())
                QueueJob(
                    id = summary.id,
                    presetId = summary.presetId,
                    createdAt = summary.createdAt,
                    payload = payload
                )
            }
        }.getOrElse {
            LogBuffer.add("QueueJobRepository.loadJob($id): ${it.stackTraceToString()}")
            null
        }
    }

    suspend fun saveJob(job: QueueJob): QueueJob = withContext(Dispatchers.IO) {
        runCatching {
            payloadFile(job.id).writeText(json.encodeToString(job.payload))

            dataStore.edit { prefs ->
                val existing = json.decodeFromString<List<JobSummary>>(prefs[jobsKey] ?: "[]")
                val updated = existing.filter { it.id != job.id } + JobSummary(
                    id = job.id,
                    presetId = job.presetId,
                    createdAt = job.createdAt
                )
                prefs[jobsKey] = json.encodeToString(updated)
            }
            job
        }.getOrElse {
            LogBuffer.add("QueueJobRepository.saveJob(${job.id}): ${it.stackTraceToString()}")
            job
        }
    }

    suspend fun deleteJob(id: Int) = withContext(Dispatchers.IO) {
        runCatching {
            payloadFile(id).delete()
            dataStore.edit { prefs ->
                val existing = json.decodeFromString<List<JobSummary>>(prefs[jobsKey] ?: "[]")
                prefs[jobsKey] = json.encodeToString(existing.filter { it.id != id })
            }
        }.getOrElse {
            LogBuffer.add("QueueJobRepository.deleteJob($id): ${it.stackTraceToString()}")
        }
    }

    suspend fun deleteJobsForPreset(presetId: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first()
            val summaries = json.decodeFromString<List<JobSummary>>(prefs[jobsKey] ?: "[]")
            summaries.filter { it.presetId == presetId }.forEach {
                payloadFile(it.id).delete()
            }
            dataStore.edit { prefs ->
                prefs[jobsKey] = json.encodeToString(summaries.filter { it.presetId != presetId })
            }
        }.getOrElse {
            LogBuffer.add("QueueJobRepository.deleteJobsForPreset($presetId): ${it.stackTraceToString()}")
        }
    }

    suspend fun renumberJobs(presetId: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first()
            val summaries = json.decodeFromString<List<JobSummary>>(prefs[jobsKey] ?: "[]")
            val presetSummaries = summaries.filter { it.presetId == presetId }.sortedBy { it.createdAt }
            val otherSummaries = summaries.filter { it.presetId != presetId }

            val newPresetSummaries = presetSummaries.mapIndexed { index, summary ->
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

            val allSummaries = otherSummaries + newPresetSummaries
            val maxId = allSummaries.maxOfOrNull { it.id } ?: 0

            dataStore.edit { prefs ->
                prefs[jobsKey] = json.encodeToString(allSummaries)
                prefs[nextIdKey] = maxId + 1
            }
        }.getOrElse {
            LogBuffer.add("QueueJobRepository.renumberJobs($presetId): ${it.stackTraceToString()}")
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
    private data class JobSummary(
        val id: Int,
        val presetId: Int,
        val createdAt: Long
    )
}
