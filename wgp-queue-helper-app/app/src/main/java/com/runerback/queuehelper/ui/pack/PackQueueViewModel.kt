package com.runerback.queuehelper.ui.pack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runerback.queuehelper.data.local.QueueJobRepository
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.model.QueueJob
import com.runerback.queuehelper.data.model.SubjectDefault
import com.runerback.queuehelper.data.model.SubjectDefaults
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.parseSubjectDefinitions
import com.runerback.queuehelper.data.template.TemplateLoader
import com.runerback.queuehelper.ui.components.LogBuffer
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PackQueueViewModel(
    private val presetId: Int,
    private val taskRepository: TaskRepository,
    private val queueJobRepository: QueueJobRepository,
    private val templateLoader: TemplateLoader
) : ViewModel() {

    var jobs by mutableStateOf<List<QueueJob>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadJobs()
    }

    fun loadJobs() {
        viewModelScope.launch {
            isLoading = true
            jobs = runCatching { queueJobRepository.loadJobs(presetId) }.getOrElse {
                LogBuffer.add("PackQueueViewModel.loadJobs($presetId): ${it.stackTraceToString()}")
                emptyList()
            }
            isLoading = false
        }
    }

    fun createJobFromPreset() {
        viewModelScope.launch {
            runCatching {
                val preset = taskRepository.loadTask(presetId) ?: return@launch
                val id = queueJobRepository.nextId()
                val job = buildJob(id, preset.payload)
                queueJobRepository.saveJob(job)
                jobs = queueJobRepository.loadJobs(presetId)
            }.onFailure {
                LogBuffer.add("PackQueueViewModel.createJobFromPreset($presetId): ${it.stackTraceToString()}")
            }
        }
    }

    fun deleteJobAndRenumber(jobId: Int) {
        viewModelScope.launch {
            runCatching {
                queueJobRepository.deleteJob(jobId)
                queueJobRepository.renumberJobs(presetId)
                jobs = queueJobRepository.loadJobs(presetId)
            }.onFailure {
                LogBuffer.add("PackQueueViewModel.deleteJobAndRenumber($jobId): ${it.stackTraceToString()}")
            }
        }
    }

    private fun buildJob(id: Int, presetPayload: JsonObject): QueueJob {
        val baseParams = presetPayload["params"]?.jsonObject ?: JsonObject(emptyMap())
        val updatedParams = JsonObject(
            baseParams.toMutableMap().apply {
                put("id", JsonPrimitive(id))
            }
        )

        val promptString = baseParams["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
        val parsed = parseSubjectDefinitions(
            com.runerback.queuehelper.data.model.MiniMaxH3Ref2VaPrompt.parse(promptString).subjectDefinitions
        )
        val defaults = SubjectDefaults(
            subjects = parsed.first.map { SubjectDefault(it.number, it.description) },
            audio = parsed.second ?: SubjectDefinition.defaultAudioDefinition()
        )

        val payload = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("params", updatedParams)
            put("subject_defaults", Json.encodeToJsonElement(SubjectDefaults.serializer(), defaults))
        }

        return QueueJob(
            id = id,
            presetId = presetId,
            createdAt = System.currentTimeMillis(),
            payload = payload
        )
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val presetId: Int,
        private val taskRepository: TaskRepository,
        private val queueJobRepository: QueueJobRepository,
        private val templateLoader: TemplateLoader
    ) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return PackQueueViewModel(presetId, taskRepository, queueJobRepository, templateLoader) as T
        }
    }
}
