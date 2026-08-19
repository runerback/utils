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
import com.runerback.queuehelper.data.model.Task
import com.runerback.queuehelper.data.model.parseSubjectDefinitions
import com.runerback.queuehelper.data.template.TemplateLoader
import com.runerback.queuehelper.ui.components.LogBuffer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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

    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showCreateDialog by mutableStateOf(false)
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
