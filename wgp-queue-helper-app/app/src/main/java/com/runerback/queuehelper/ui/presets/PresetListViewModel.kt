package com.runerback.queuehelper.ui.presets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runerback.queuehelper.data.local.PresetRepository
import com.runerback.queuehelper.data.model.ExportedPreset
import com.runerback.queuehelper.data.model.MiniMaxH3Ref2VaPrompt
import com.runerback.queuehelper.data.model.Preset
import com.runerback.queuehelper.data.model.SubjectDefault
import com.runerback.queuehelper.data.model.SubjectDefaults
import com.runerback.queuehelper.data.model.SubjectDefinition
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

class PresetListViewModel(
    private val repository: PresetRepository,
    private val templateLoader: TemplateLoader
) : ViewModel() {

    private val exportJson = Json { prettyPrint = true }
    private val importJson = Json { ignoreUnknownKeys = true }

    var presets by mutableStateOf<List<Preset>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showCreateDialog by mutableStateOf(false)
        private set

    var selectionMode by mutableStateOf(false)
        private set

    var selectedPresetIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    private val _events = MutableSharedFlow<PresetListEvent>()
    val events: SharedFlow<PresetListEvent> = _events.asSharedFlow()

    init {
        loadPresets()
    }

    fun loadPresets() {
        viewModelScope.launch {
            isLoading = true
            presets = runCatching { repository.loadPresets() }.getOrElse {
                LogBuffer.add("PresetListViewModel.loadPresets: ${it.stackTraceToString()}")
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
        if (!selectionMode) selectedPresetIds = emptySet()
    }

    fun setSelected(presetId: Int, selected: Boolean) {
        selectedPresetIds = if (selected) {
            selectedPresetIds + presetId
        } else {
            selectedPresetIds - presetId
        }
    }

    fun selectAll() {
        selectedPresetIds = presets.map { it.id }.toSet()
    }

    fun createPreset(name: String, modelType: String) {
        viewModelScope.launch {
            runCatching {
                val id = repository.nextId()
                val preset = buildPreset(id, name, modelType)
                repository.savePreset(preset)
                presets = repository.loadPresets()
                showCreateDialog = false
                _events.emit(PresetListEvent.NavigateToEdit(preset.id))
            }.onFailure {
                LogBuffer.add("PresetListViewModel.createPreset: ${it.stackTraceToString()}")
            }
        }
    }

    fun deletePreset(preset: Preset) {
        viewModelScope.launch {
            runCatching {
                repository.deletePreset(preset.id)
                presets = repository.loadPresets()
            }.onFailure {
                LogBuffer.add("PresetListViewModel.deletePreset(${preset.id}): ${it.stackTraceToString()}")
            }
        }
    }

    fun duplicatePreset(preset: Preset) {
        viewModelScope.launch {
            runCatching {
                val existing = repository.loadPresets()
                val newId = repository.nextId()
                val newName = uniqueName(preset.name, existing)
                val newPreset = Preset(
                    id = newId,
                    name = newName,
                    modelType = preset.modelType,
                    createdAt = System.currentTimeMillis(),
                    payload = updatePayloadId(preset.payload, newId)
                )
                repository.savePreset(newPreset)
                presets = repository.loadPresets()
                _events.emit(PresetListEvent.NavigateToEdit(newPreset.id))
            }.onFailure {
                LogBuffer.add("PresetListViewModel.duplicatePreset(${preset.id}): ${it.stackTraceToString()}")
            }
        }
    }

    fun exportPresets(): String {
        val selected = presets.filter { it.id in selectedPresetIds }
        val exported = selected.map { preset ->
            ExportedPreset(
                id = preset.id,
                name = preset.name,
                modelType = preset.modelType,
                createdAt = preset.createdAt,
                payload = preset.payload
            )
        }
        return exportJson.encodeToString(exported)
    }

    fun importPresets(jsonString: String) {
        viewModelScope.launch {
            runCatching {
                val imported = importJson.decodeFromString<List<ExportedPreset>>(jsonString)
                val existing = repository.loadPresets().toMutableList()
                var count = 0
                imported.forEach { exported ->
                    val uniqueName = uniqueName(exported.name, exported.modelType, existing)
                    val newId = repository.nextId()
                    val preset = Preset(
                        id = newId,
                        name = uniqueName,
                        modelType = exported.modelType,
                        createdAt = System.currentTimeMillis(),
                        payload = updatePayloadId(exported.payload, newId)
                    )
                    repository.savePreset(preset)
                    existing.add(preset)
                    count++
                }
                presets = repository.loadPresets()
                _events.emit(PresetListEvent.ShowMessage("Imported $count presets"))
            }.onFailure {
                LogBuffer.add("PresetListViewModel.importPresets: ${it.stackTraceToString()}")
                _events.emit(PresetListEvent.ShowMessage("Import failed"))
            }
        }
    }

    private fun buildPreset(id: Int, name: String, modelType: String): Preset {
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
        return Preset(
            id = id,
            name = name.ifBlank { templateLoader.defaultName(modelType) },
            modelType = modelType,
            createdAt = System.currentTimeMillis(),
            payload = payload
        )
    }

    sealed class PresetListEvent {
        data class NavigateToEdit(val presetId: Int) : PresetListEvent()
        data class ShowMessage(val message: String) : PresetListEvent()
    }

    private fun uniqueName(name: String, existing: List<Preset>): String {
        if (existing.none { it.name == name }) return name
        var counter = 2
        while (true) {
            val candidate = "$name ($counter)"
            if (existing.none { it.name == candidate }) return candidate
            counter++
        }
    }

    private fun uniqueName(name: String, modelType: String, existing: List<Preset>): String {
        return uniqueName(name, existing.filter { it.modelType == modelType })
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
        private val repository: PresetRepository,
        private val templateLoader: TemplateLoader
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PresetListViewModel(repository, templateLoader) as T
        }
    }
}
