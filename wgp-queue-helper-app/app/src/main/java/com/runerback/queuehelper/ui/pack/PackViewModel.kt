package com.runerback.queuehelper.ui.pack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runerback.queuehelper.data.local.PresetRepository
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.model.SubjectDefault
import com.runerback.queuehelper.data.model.SubjectDefaults
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.Task
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

class PackViewModel(
    private val presetId: Int,
    private val presetRepository: PresetRepository,
    private val taskRepository: TaskRepository,
    private val templateLoader: TemplateLoader
) : ViewModel() {

    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set

    var presetName by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadTasks()
    }

    fun loadTasks() {
        viewModelScope.launch {
            isLoading = true
            tasks = runCatching { taskRepository.loadTasks(presetId) }.getOrElse {
                LogBuffer.add("PackViewModel.loadTasks($presetId): ${it.stackTraceToString()}")
                emptyList()
            }
            presetName = runCatching { presetRepository.loadPreset(presetId)?.name }.getOrElse {
                LogBuffer.add("PackViewModel.loadPresetName($presetId): ${it.stackTraceToString()}")
                null
            } ?: ""
            isLoading = false
        }
    }

    fun createTaskFromPreset() {
        viewModelScope.launch {
            runCatching {
                val preset = presetRepository.loadPreset(presetId) ?: return@launch
                val id = taskRepository.nextId()
                val task = buildTask(id, preset.payload)
                taskRepository.saveTask(task)
                tasks = taskRepository.loadTasks(presetId)
            }.onFailure {
                LogBuffer.add("PackViewModel.createTaskFromPreset($presetId): ${it.stackTraceToString()}")
            }
        }
    }

    fun deleteTaskAndRenumber(taskId: Int) {
        viewModelScope.launch {
            runCatching {
                taskRepository.deleteTask(taskId)
                taskRepository.renumberTasks(presetId)
                tasks = taskRepository.loadTasks(presetId)
            }.onFailure {
                LogBuffer.add("PackViewModel.deleteTaskAndRenumber($taskId): ${it.stackTraceToString()}")
            }
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            runCatching {
                taskRepository.deleteTasksForPreset(presetId)
                tasks = emptyList()
            }.onFailure {
                LogBuffer.add("PackViewModel.clearAllTasks($presetId): ${it.stackTraceToString()}")
            }
        }
    }

    private fun buildTask(id: Int, presetPayload: JsonObject): Task {
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

        return Task(
            id = id,
            presetId = presetId,
            createdAt = System.currentTimeMillis(),
            payload = payload
        )
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val presetId: Int,
        private val presetRepository: PresetRepository,
        private val taskRepository: TaskRepository,
        private val templateLoader: TemplateLoader
    ) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return PackViewModel(presetId, presetRepository, taskRepository, templateLoader) as T
        }
    }
}
