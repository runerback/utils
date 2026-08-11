package com.runerback.comfyuiapi.ui

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.comfyuiapi.data.model.EditableParameter
import com.runerback.comfyuiapi.data.model.FieldType
import com.runerback.comfyuiapi.data.model.GenerationStatus
import com.runerback.comfyuiapi.data.model.OutputImage
import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.data.model.UiState
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.data.repository.ComfyRepository
import com.runerback.comfyuiapi.data.repository.GenerationResult
import com.runerback.comfyuiapi.data.repository.LoadResult
import com.runerback.comfyuiapi.ui.components.LogBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ComfyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadedWorkflow: Workflow? = null

    init {
        viewModelScope.launch {
            repository.serverUrl.collect { url ->
                _uiState.update { it.copy(serverUrl = url) }
            }
        }
        viewModelScope.launch {
            repository.serverUrlHistory.collect { history ->
                _uiState.update { it.copy(serverUrlHistory = history) }
            }
        }
        viewModelScope.launch {
            repository.generationTimeoutMs.collect { ms ->
                _uiState.update { it.copy(generationTimeoutMs = ms) }
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url) }
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            val url = _uiState.value.serverUrl
            repository.saveServerUrl(url)
            repository.addServerUrlToHistory(url)
        }
    }

    fun onGenerationTimeoutChange(ms: Long) {
        _uiState.update { it.copy(generationTimeoutMs = ms) }
    }

    fun saveGenerationTimeout() {
        viewModelScope.launch {
            repository.saveGenerationTimeoutMs(_uiState.value.generationTimeoutMs)
        }
    }

    fun loadWorkflow(uri: Uri) {
        LogBuffer.add("loadWorkflow: $uri")
        viewModelScope.launch {
            when (val result = repository.loadWorkflow(uri)) {
                is LoadResult.Success -> {
                    LogBuffer.add("loadWorkflow success: ${result.value.size} nodes")
                    loadedWorkflow = result.value
                    _uiState.update {
                        it.copy(
                            workflowName = result.name,
                            parameters = emptyList(),
                            currentValues = emptyMap(),
                            outputs = emptyList(),
                            preview = null,
                            errorMessage = null
                        )
                    }
                }
                is LoadResult.Error -> {
                    LogBuffer.add("loadWorkflow error: ${result.message}")
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun loadSchema(uri: Uri) {
        val workflow = loadedWorkflow ?: run {
            _uiState.update { it.copy(errorMessage = "Load a workflow first") }
            return
        }
        LogBuffer.add("loadSchema: $uri")
        viewModelScope.launch {
            when (val result = repository.loadSchema(uri, workflow)) {
                is LoadResult.Success -> {
                    LogBuffer.add("loadSchema success: ${result.value.size} parameters")
                    val values = repository.initialValues(workflow, result.value)
                    _uiState.update {
                        it.copy(
                            schemaName = result.name,
                            parameters = result.value,
                            currentValues = values,
                            outputs = emptyList(),
                            preview = null,
                            errorMessage = null
                        )
                    }
                }
                is LoadResult.Error -> {
                    LogBuffer.add("loadSchema error: ${result.message}")
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun updateValue(parameter: EditableParameter, value: JsonElement) {
        _uiState.update { state ->
            state.copy(
                currentValues = state.currentValues.toMutableMap().apply {
                    put(ParameterKey(parameter.nodeId, parameter.path), value)
                }
            )
        }
    }

    fun randomizeSeeds() {
        _uiState.update { state ->
            val newValues = state.currentValues.toMutableMap()
            state.parameters.filter { it.type == FieldType.SeedType }.forEach { param ->
                newValues[ParameterKey(param.nodeId, param.path)] = JsonPrimitive(Random.nextLong(0, Long.MAX_VALUE))
            }
            state.copy(currentValues = newValues)
        }
    }

    fun setUploadUri(parameter: EditableParameter, uri: Uri?) {
        _uiState.update { state ->
            state.copy(
                pendingUploads = state.pendingUploads.toMutableMap().apply {
                    val key = ParameterKey(parameter.nodeId, parameter.path)
                    if (uri != null) put(key, uri) else remove(key)
                }
            )
        }
    }

    fun generate() {
        val workflow = loadedWorkflow ?: return
        val serverUrl = _uiState.value.serverUrl
        LogBuffer.add("generate: serverUrl=$serverUrl, workflow nodes=${workflow.size}")
        if (serverUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Server URL is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    generationStatus = GenerationStatus.Connecting,
                    preview = null,
                    outputs = emptyList(),
                    errorMessage = null
                )
            }

            val currentState = _uiState.value
            val pending = currentState.pendingUploads
            LogBuffer.add("generate: pending uploads=${pending.size}")

            val uploadReplacements = mutableMapOf<ParameterKey, JsonElement>()

            for ((key, uri) in pending) {
                val param = currentState.parameters.find {
                    ParameterKey(it.nodeId, it.path) == key
                } ?: continue
                val uploadType = (param.type as? FieldType.UploadType)?.uploadType ?: "input"
                LogBuffer.add("generate: uploading $key type=$uploadType uri=$uri")
                val result = repository.uploadImage(serverUrl, uri, uploadType)
                if (result.isSuccess) {
                    val filename = result.getOrThrow()
                    LogBuffer.add("generate: upload success filename=$filename")
                    uploadReplacements[key] = JsonPrimitive(filename)
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "Unknown upload error"
                    LogBuffer.add("generate: upload failed: $msg")
                    _uiState.update {
                        it.copy(
                            generationStatus = GenerationStatus.Error("Upload failed: $msg"),
                            errorMessage = msg
                        )
                    }
                    return@launch
                }
            }

            val valuesWithUploads = currentState.currentValues.toMutableMap().apply {
                putAll(uploadReplacements)
            }
            _uiState.update { it.copy(currentValues = valuesWithUploads) }

            val patched = repository.patchWorkflow(workflow, valuesWithUploads)
            LogBuffer.add("generate: patched workflow, queueing prompt")

            repository.generate(serverUrl, patched).collect { result ->
                when (result) {
                    is GenerationResult.Connecting -> {
                        LogBuffer.add("generate: connecting")
                        _uiState.update { it.copy(generationStatus = GenerationStatus.Connecting) }
                    }
                    is GenerationResult.Running -> {
                        LogBuffer.add("generate: running node=${result.currentNode} progress=${result.progress}")
                        _uiState.update { state ->
                            state.copy(
                                generationStatus = GenerationStatus.Running(
                                    currentNode = result.currentNode,
                                    progress = result.progress
                                )
                            )
                        }
                    }
                    is GenerationResult.Preview -> {
                        LogBuffer.add("generate: preview received")
                        _uiState.update { it.copy(preview = result.image) }
                    }
                    is GenerationResult.Completed -> {
                        LogBuffer.add("generate: completed, outputs=${result.outputs.size}")
                        _uiState.update {
                            it.copy(
                                generationStatus = GenerationStatus.Completed(""),
                                outputs = result.outputs
                            )
                        }
                    }
                    is GenerationResult.Error -> {
                        LogBuffer.add("generate: error=${result.message}")
                        _uiState.update {
                            it.copy(
                                generationStatus = GenerationStatus.Error(result.message),
                                errorMessage = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
