package com.runerback.queuehelper.ui.pack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runerback.queuehelper.data.local.PresetRepository
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.model.Preset
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
    private val presetId: Int?,
    private val presetRepository: PresetRepository,
    private val taskRepository: TaskRepository,
    private val templateLoader: TemplateLoader
) : ViewModel() {

    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set

    var presetName by mutableStateOf("")
        private set

    var presetNameMap by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    var presets by mutableStateOf<List<Preset>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showPresetPicker by mutableStateOf(false)
        private set

    var lastSelectedPresetId by mutableStateOf<Int?>(null)
        private set

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadTasks()
        loadLastSelectedPreset()
    }

    fun loadTasks() {
        viewModelScope.launch {
            isLoading = true
            tasks = runCatching {
                if (presetId != null) {
                    taskRepository.loadTasks(presetId)
                } else {
                    taskRepository.loadAllTasks()
                }
            }.getOrElse {
                LogBuffer.add("PackViewModel.loadTasks($presetId): ${it.stackTraceToString()}")
                emptyList()
            }
            presetName = runCatching {
                presetId?.let { presetRepository.loadPreset(it)?.name }
            }.getOrElse {
                LogBuffer.add("PackViewModel.loadPresetName($presetId): ${it.stackTraceToString()}")
                null
            } ?: ""
            val loadedPresets = runCatching {
                presetRepository.loadPresets()
            }.getOrElse {
                LogBuffer.add("PackViewModel.loadPresets: ${it.stackTraceToString()}")
                emptyList()
            }
            presetNameMap = loadedPresets.associate { it.id to it.name }
            presets = loadedPresets
            isLoading = false
        }
    }

    private fun loadLastSelectedPreset() {
        if (presetId != null) return
        viewModelScope.launch {
            lastSelectedPresetId = runCatching {
                taskRepository.lastGlobalPresetId()
            }.getOrElse {
                LogBuffer.add("PackViewModel.loadLastSelectedPreset: ${it.stackTraceToString()}")
                null
            }
        }
    }

    fun requestCreateTask() {
        if (presetId != null) {
            createTaskFromPreset(presetId)
        } else {
            showPresetPicker = true
        }
    }

    fun dismissPresetPicker() {
        showPresetPicker = false
    }

    fun createTaskFromPreset(selectedPresetId: Int) {
        viewModelScope.launch {
            runCatching {
                val preset = presetRepository.loadPreset(selectedPresetId) ?: return@launch
                val id = taskRepository.nextId()
                val task = buildTask(id, selectedPresetId, preset.payload)
                taskRepository.saveTask(task)
                tasks = if (presetId != null) {
                    taskRepository.loadTasks(presetId)
                } else {
                    taskRepository.loadAllTasks()
                }
                if (presetId == null) {
                    lastSelectedPresetId = selectedPresetId
                    taskRepository.setLastGlobalPresetId(selectedPresetId)
                }
                showPresetPicker = false
            }.onFailure {
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): ${it.stackTraceToString()}")
            }
        }
    }

    fun deleteTaskAndRenumber(taskId: Int) {
        viewModelScope.launch {
            runCatching {
                taskRepository.deleteTask(taskId)
                if (presetId != null) {
                    taskRepository.renumberTasks(presetId)
                } else {
                    taskRepository.renumberAllTasks()
                }
                tasks = if (presetId != null) {
                    taskRepository.loadTasks(presetId)
                } else {
                    taskRepository.loadAllTasks()
                }
            }.onFailure {
                LogBuffer.add("PackViewModel.deleteTaskAndRenumber($taskId): ${it.stackTraceToString()}")
            }
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            runCatching {
                if (presetId != null) {
                    taskRepository.deleteTasksForPreset(presetId)
                } else {
                    taskRepository.deleteAllTasks()
                }
                tasks = emptyList()
            }.onFailure {
                LogBuffer.add("PackViewModel.clearAllTasks($presetId): ${it.stackTraceToString()}")
            }
        }
    }

    private fun buildTask(id: Int, taskPresetId: Int, presetPayload: JsonObject): Task {
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
            presetId = taskPresetId,
            createdAt = System.currentTimeMillis(),
            payload = payload
        )
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val presetId: Int?,
        private val presetRepository: PresetRepository,
        private val taskRepository: TaskRepository,
        private val templateLoader: TemplateLoader
    ) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return PackViewModel(presetId, presetRepository, taskRepository, templateLoader) as T
        }
    }
}
