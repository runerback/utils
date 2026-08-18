package com.runerback.queuehelper.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.model.MiniMaxH3Ref2VaPrompt
import com.runerback.queuehelper.data.model.SubjectDefault
import com.runerback.queuehelper.data.model.SubjectDefaults
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.Task
import com.runerback.queuehelper.data.model.formatSubjectDefinitions
import com.runerback.queuehelper.data.model.parseSubjectDefinitions
import com.runerback.queuehelper.data.template.TemplateLoader
import com.runerback.queuehelper.ui.navigation.EditTaskRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EditTaskViewModel(
    private val repository: TaskRepository,
    private val templateLoader: TemplateLoader,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val taskId: Int = savedStateHandle[EditTaskRoute::taskId.name]
        ?: throw IllegalArgumentException("Missing taskId")

    var task by mutableStateOf<Task?>(null)
        private set

    var name by mutableStateOf("")
        private set

    var prompt by mutableStateOf(MiniMaxH3Ref2VaPrompt())
        private set

    var resolution by mutableStateOf("480x832")
        private set

    val subjectDefaults = mutableStateListOf<SubjectDefault>()

    var audioDefault by mutableStateOf(SubjectDefinition.defaultAudioDefinition())
        private set

    var isLoading by mutableStateOf(false)
        private set

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadTask()
    }

    private fun loadTask() {
        viewModelScope.launch {
            isLoading = true
            val loaded = repository.loadTask(taskId)
            task = loaded
            loaded?.let {
                name = it.name
                val params = it.payload["params"]?.jsonObject ?: JsonObject(emptyMap())
                resolution = params["resolution"]?.jsonPrimitive?.contentOrNull ?: "480x832"
                val promptString = params["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
                prompt = MiniMaxH3Ref2VaPrompt.parse(promptString)

                val defaultsJson = it.payload["subject_defaults"]
                if (defaultsJson != null) {
                    try {
                        val defaults = json.decodeFromJsonElement(
                            SubjectDefaults.serializer(),
                            defaultsJson
                        )
                        subjectDefaults.clear()
                        subjectDefaults.addAll(defaults.subjects)
                        audioDefault = defaults.audio
                    } catch (_: Exception) {
                        loadDefaultsFromPrompt()
                    }
                } else {
                    loadDefaultsFromPrompt()
                }
            }
            isLoading = false
        }
    }

    private fun loadDefaultsFromPrompt() {
        val parsed = parseSubjectDefinitions(prompt.subjectDefinitions)
        subjectDefaults.clear()
        subjectDefaults.addAll(parsed.first.map { SubjectDefault(it.number, it.description) })
        audioDefault = parsed.second ?: SubjectDefinition.defaultAudioDefinition()
    }

    fun updateName(value: String) {
        name = value
    }

    fun updatePrompt(value: MiniMaxH3Ref2VaPrompt) {
        prompt = value
    }

    fun updateResolution(value: String) {
        resolution = value
    }

    fun addDefaultSubject(description: String) {
        val number = (subjectDefaults.maxOfOrNull { it.number } ?: 0) + 1
        subjectDefaults.add(SubjectDefault(number, description))
    }

    fun updateDefaultSubject(number: Int, description: String) {
        val index = subjectDefaults.indexOfFirst { it.number == number }
        if (index != -1) {
            subjectDefaults[index] = SubjectDefault(number, description)
        }
    }

    fun removeDefaultSubject(number: Int) {
        subjectDefaults.removeAll { it.number == number }
    }

    fun updateAudioDefault(value: String) {
        audioDefault = value
    }

    fun save() {
        viewModelScope.launch {
            task?.let { current ->
                val subjectDefinitions = formatSubjectDefinitions(
                    subjectDefaults.map { SubjectDefinition(0, it.number, it.description) },
                    audioDefault
                )
                val updatedPrompt = prompt.copy(subjectDefinitions = subjectDefinitions)

                val params = current.payload["params"]?.jsonObject
                    ?.toMutableMap()
                    ?: mutableMapOf()
                params["prompt"] = JsonPrimitive(updatedPrompt.toPromptString())
                params["resolution"] = JsonPrimitive(resolution)

                val defaults = SubjectDefaults(subjectDefaults.toList(), audioDefault)
                val updatedPayload = JsonObject(
                    current.payload.toMutableMap().apply {
                        put("params", JsonObject(params))
                        put(
                            "subject_defaults",
                            Json.encodeToJsonElement(SubjectDefaults.serializer(), defaults)
                        )
                    }
                )
                val updated = current.copy(name = name, payload = updatedPayload)
                repository.saveTask(updated)
                task = updated
                prompt = updatedPrompt
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val repository: TaskRepository,
        private val templateLoader: TemplateLoader
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return EditTaskViewModel(
                repository,
                templateLoader,
                extras.createSavedStateHandle()
            ) as T
        }
    }
}
