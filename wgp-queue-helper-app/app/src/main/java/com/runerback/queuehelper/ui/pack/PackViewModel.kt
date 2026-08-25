package com.runerback.queuehelper.ui.pack

import android.util.Log
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

    companion object {
        private const val TAG = "PackViewModel"
    }

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
            Log.d(TAG, "loadTasks() started, presetId=$presetId")
            LogBuffer.add("PackViewModel.loadTasks() started, presetId=$presetId")
            tasks = runCatching {
                if (presetId != null) {
                    taskRepository.loadTasks(presetId)
                } else {
                    taskRepository.loadAllTasks()
                }
            }.getOrElse {
                Log.e(TAG, "loadTasks($presetId) failed", it)
                LogBuffer.add("PackViewModel.loadTasks($presetId): ${it.stackTraceToString()}")
                emptyList()
            }
            Log.d(TAG, "loadTasks() loaded ${tasks.size} tasks")
            LogBuffer.add("PackViewModel.loadTasks() loaded ${tasks.size} tasks")
            presetName = runCatching {
                presetId?.let { presetRepository.loadPreset(it)?.name }
            }.getOrElse {
                Log.e(TAG, "loadPresetName($presetId) failed", it)
                LogBuffer.add("PackViewModel.loadPresetName($presetId): ${it.stackTraceToString()}")
                null
            } ?: ""
            val loadedPresets = runCatching {
                presetRepository.loadPresets()
            }.getOrElse {
                Log.e(TAG, "loadPresets() failed", it)
                LogBuffer.add("PackViewModel.loadPresets: ${it.stackTraceToString()}")
                emptyList()
            }
            Log.d(TAG, "loadTasks() loaded ${loadedPresets.size} presets")
            LogBuffer.add("PackViewModel.loadTasks() loaded ${loadedPresets.size} presets")
            presetNameMap = loadedPresets.associate { it.id to it.name }
            presets = loadedPresets
            isLoading = false
            Log.d(TAG, "loadTasks() finished")
            LogBuffer.add("PackViewModel.loadTasks() finished")
        }
    }

    private fun loadLastSelectedPreset() {
        if (presetId != null) return
        viewModelScope.launch {
            Log.d(TAG, "loadLastSelectedPreset() started")
            LogBuffer.add("PackViewModel.loadLastSelectedPreset() started")
            lastSelectedPresetId = runCatching {
                taskRepository.lastGlobalPresetId()
            }.getOrElse {
                Log.e(TAG, "loadLastSelectedPreset() failed", it)
                LogBuffer.add("PackViewModel.loadLastSelectedPreset: ${it.stackTraceToString()}")
                null
            }
            Log.d(TAG, "loadLastSelectedPreset() loaded lastSelectedPresetId=$lastSelectedPresetId")
            LogBuffer.add("PackViewModel.loadLastSelectedPreset() loaded lastSelectedPresetId=$lastSelectedPresetId")
        }
    }

    fun requestCreateTask() {
        Log.d(TAG, "requestCreateTask() clicked, presetId=$presetId")
        LogBuffer.add("PackViewModel.requestCreateTask() clicked, presetId=$presetId")
        if (presetId != null) {
            Log.d(TAG, "requestCreateTask() in preset mode, creating from preset $presetId")
            LogBuffer.add("PackViewModel.requestCreateTask() in preset mode, creating from preset $presetId")
            createTaskFromPreset(presetId)
        } else {
            Log.d(TAG, "requestCreateTask() in global mode, showing preset picker, presets=${presets.size}")
            LogBuffer.add("PackViewModel.requestCreateTask() in global mode, showing preset picker, presets=${presets.size}")
            showPresetPicker = true
        }
    }

    fun dismissPresetPicker() {
        showPresetPicker = false
    }

    fun createTaskFromPreset(selectedPresetId: Int) {
        Log.d(TAG, "createTaskFromPreset($selectedPresetId) called, current presetId=$presetId")
        LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId) called, current presetId=$presetId")
        viewModelScope.launch {
            runCatching {
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): loading preset...")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): loading preset...")
                val preset = presetRepository.loadPreset(selectedPresetId)
                if (preset == null) {
                    Log.e(TAG, "createTaskFromPreset($selectedPresetId): preset load returned null, aborting")
                    LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): preset load returned null, aborting")
                    return@launch
                }
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): preset loaded name=${preset.name}")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): preset loaded name=${preset.name}")
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): generating next task id...")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): generating next task id...")
                val id = taskRepository.nextId()
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): generated task id=$id")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): generated task id=$id")
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): building task...")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): building task...")
                val task = buildTask(id, selectedPresetId, preset.payload)
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): built task presetId=${task.presetId}")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): built task presetId=${task.presetId}")
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): saving task...")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): saving task...")
                taskRepository.saveTask(task)
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): task saved")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): task saved")
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): reloading tasks...")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): reloading tasks...")
                tasks = if (presetId != null) {
                    taskRepository.loadTasks(presetId)
                } else {
                    taskRepository.loadAllTasks()
                }
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): reloaded ${tasks.size} tasks")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): reloaded ${tasks.size} tasks")
                if (presetId == null) {
                    Log.d(TAG, "createTaskFromPreset($selectedPresetId): updating last global preset id")
                    LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): updating last global preset id")
                    lastSelectedPresetId = selectedPresetId
                    taskRepository.setLastGlobalPresetId(selectedPresetId)
                }
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): dismissing picker")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): dismissing picker")
                showPresetPicker = false
                Log.d(TAG, "createTaskFromPreset($selectedPresetId): finished successfully")
                LogBuffer.add("PackViewModel.createTaskFromPreset($selectedPresetId): finished successfully")
            }.onFailure {
                Log.e(TAG, "createTaskFromPreset($selectedPresetId) failed", it)
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
        Log.d(TAG, "buildTask(id=$id, taskPresetId=$taskPresetId): starting")
        LogBuffer.add("PackViewModel.buildTask(id=$id, taskPresetId=$taskPresetId): starting")
        val baseParams = presetPayload["params"]?.jsonObject ?: JsonObject(emptyMap())
        val updatedParams = JsonObject(
            baseParams.toMutableMap().apply {
                put("id", JsonPrimitive(id))
            }
        )

        val promptString = baseParams["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
        Log.d(TAG, "buildTask(id=$id): prompt length=${promptString.length}")
        LogBuffer.add("PackViewModel.buildTask(id=$id): prompt length=${promptString.length}")
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
        Log.d(TAG, "buildTask(id=$id): completed")
        LogBuffer.add("PackViewModel.buildTask(id=$id): completed")

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
