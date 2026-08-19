package com.runerback.queuehelper.ui.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.model.MiniMaxH3Ref2VaPrompt
import com.runerback.queuehelper.data.model.SubjectDefault
import com.runerback.queuehelper.data.model.SubjectDefaults
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.ExportedTask
import com.runerback.queuehelper.data.model.Task
import com.runerback.queuehelper.data.model.parseSubjectDefinitions
import com.runerback.queuehelper.data.template.TemplateLoader
import com.runerback.queuehelper.ui.components.LogBuffer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TaskListViewModel(
    private val repository: TaskRepository,
    private val templateLoader: TemplateLoader
) : ViewModel() {

    private val exportJson = Json { prettyPrint = true }
    private val importJson = Json { ignoreUnknownKeys = true }

    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showCreateDialog by mutableStateOf(false)
        private set

    var selectionMode by mutableStateOf(false)
        private set

    var selectedTaskIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    private val _events = MutableSharedFlow<TaskListEvent>()
    val events: SharedFlow<TaskListEvent> = _events.asSharedFlow()

    init {
        loadTasks()
    }

    fun loadTasks() {
        viewModelScope.launch {
            isLoading = true
            tasks = runCatching { repository.loadTasks() }.getOrElse {
                LogBuffer.add("TaskListViewModel.loadTasks: ${it.stackTraceToString()}")
                emptyList()
            }
            isLoading = false
        }
    }

    fun openCreateDialog() {
        showCreateDialog = true
    }

    fun closeCreateDialog() {
        showCreateDialog = false
    }

    fun toggleSelectionMode() {
        selectionMode = !selectionMode
        if (!selectionMode) selectedTaskIds = emptySet()
    }

    fun setSelected(taskId: Int, selected: Boolean) {
        selectedTaskIds = if (selected) {
            selectedTaskIds + taskId
        } else {
            selectedTaskIds - taskId
        }
    }

    fun selectAll() {
        selectedTaskIds = tasks.map { it.id }.toSet()
    }

    fun createTask(name: String, modelType: String) {
        viewModelScope.launch {
            runCatching {
                val id = repository.nextId()
                val task = buildTask(id, name, modelType)
                repository.saveTask(task)
                tasks = repository.loadTasks()
                showCreateDialog = false
                _events.emit(TaskListEvent.NavigateToEdit(task.id))
            }.onFailure {
                LogBuffer.add("TaskListViewModel.createTask: ${it.stackTraceToString()}")
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            runCatching {
                repository.deleteTask(task.id)
                tasks = repository.loadTasks()
            }.onFailure {
                LogBuffer.add("TaskListViewModel.deleteTask(${task.id}): ${it.stackTraceToString()}")
            }
        }
    }

    fun exportTasks(): String {
        val selected = tasks.filter { it.id in selectedTaskIds }
        val exported = selected.map { task ->
            ExportedTask(
                id = task.id,
                name = task.name,
                modelType = task.modelType,
                createdAt = task.createdAt,
                payload = task.payload
            )
        }
        return exportJson.encodeToString(exported)
    }

    fun importTasks(jsonString: String) {
        viewModelScope.launch {
            runCatching {
                val imported = importJson.decodeFromString<List<ExportedTask>>(jsonString)
                val existing = repository.loadTasks().toMutableList()
                var count = 0
                imported.forEach { exported ->
                    val uniqueName = uniqueName(exported.name, exported.modelType, existing)
                    val newId = repository.nextId()
                    val task = Task(
                        id = newId,
                        name = uniqueName,
                        modelType = exported.modelType,
                        createdAt = System.currentTimeMillis(),
                        payload = updatePayloadId(exported.payload, newId)
                    )
                    repository.saveTask(task)
                    existing.add(task)
                    count++
                }
                tasks = repository.loadTasks()
                _events.emit(TaskListEvent.ShowMessage("Imported $count presets"))
            }.onFailure {
                LogBuffer.add("TaskListViewModel.importTasks: ${it.stackTraceToString()}")
                _events.emit(TaskListEvent.ShowMessage("Import failed"))
            }
        }
    }

    private fun buildTask(id: Int, name: String, modelType: String): Task {
        val base = templateLoader.basePayload(modelType)
        val params = base["params"]?.jsonObject ?: JsonObject(emptyMap())
        val updatedParams = JsonObject(
            params.toMutableMap().apply {
                put("id", JsonPrimitive(id))
                put("model_type", JsonPrimitive(modelType))
                put("base_model_type", JsonPrimitive(modelType))
            }
        )

        val promptString = params["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
        val parsed = parseSubjectDefinitions(
            MiniMaxH3Ref2VaPrompt.parse(promptString).subjectDefinitions
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
            name = name.ifBlank { templateLoader.defaultName(modelType) },
            modelType = modelType,
            createdAt = System.currentTimeMillis(),
            payload = payload
        )
    }

    sealed class TaskListEvent {
        data class NavigateToEdit(val taskId: Int) : TaskListEvent()
        data class ShowMessage(val message: String) : TaskListEvent()
    }

    private fun uniqueName(name: String, modelType: String, existing: List<Task>): String {
        val sameType = existing.filter { it.modelType == modelType }
        if (sameType.none { it.name == name }) return name
        var counter = 2
        while (true) {
            val candidate = "$name ($counter)"
            if (sameType.none { it.name == candidate }) return candidate
            counter++
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

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val repository: TaskRepository,
        private val templateLoader: TemplateLoader
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskListViewModel(repository, templateLoader) as T
        }
    }
}
