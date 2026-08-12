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
import com.runerback.comfyuiapi.domain.extractOptions
import com.runerback.comfyuiapi.domain.resolveOptionSource
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
                            errorMessage = null,
                            fixedSeeds = emptySet(),
                            optionLists = emptyMap(),
                            optionLoading = emptySet()
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
                            errorMessage = null,
                            fixedSeeds = emptySet(),
                            optionLists = emptyMap(),
                            optionLoading = emptySet()
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

    fun toggleFixedSeed(parameter: EditableParameter) {
        val key = ParameterKey(parameter.nodeId, parameter.path)
        _uiState.update { state ->
            state.copy(
                fixedSeeds = if (state.fixedSeeds.contains(key)) {
                    state.fixedSeeds - key
                } else {
                    state.fixedSeeds + key
                }
            )
        }
    }

    private fun randomizeAllSeeds() {
        _uiState.update { state ->
            val newValues = state.currentValues.toMutableMap()
            state.parameters
                .filter { it.type == FieldType.SeedType }
                .filter { ParameterKey(it.nodeId, it.path) !in state.fixedSeeds }
                .forEach { param ->
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

    fun onBatchCountChange(count: Int) {
        _uiState.update { it.copy(batchCount = count.coerceAtLeast(1)) }
    }

    fun generate() {
        val workflow = loadedWorkflow ?: return
        val serverUrl = _uiState.value.serverUrl
        val totalBatches = _uiState.value.batchCount.coerceAtLeast(1)
        LogBuffer.add("generate: serverUrl=$serverUrl, workflow nodes=${workflow.size}, batches=$totalBatches")
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

            val initialState = _uiState.value
            val pending = initialState.pendingUploads
            LogBuffer.add("generate: pending uploads=${pending.size}")

            val uploadReplacements = mutableMapOf<ParameterKey, JsonElement>()

            for ((key, uri) in pending) {
                val param = initialState.parameters.find {
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

            var batchFailed = false

            for (batchIndex in 0 until totalBatches) {
                val currentState = _uiState.value
                val valuesWithUploads = currentState.currentValues.toMutableMap().apply {
                    putAll(uploadReplacements)
                }
                _uiState.update { it.copy(currentValues = valuesWithUploads) }

                val patched = repository.patchWorkflow(workflow, valuesWithUploads)
                LogBuffer.add("generate: patched workflow, queueing prompt batch ${batchIndex + 1}/$totalBatches")

                repository.generate(serverUrl, patched).collect { result ->
                    when (result) {
                        is GenerationResult.Connecting -> {
                            LogBuffer.add("generate: connecting batch ${batchIndex + 1}")
                            _uiState.update { state ->
                                state.copy(
                                    generationStatus = GenerationStatus.Running(
                                        currentBatch = batchIndex + 1,
                                        totalBatches = totalBatches
                                    )
                                )
                            }
                        }
                        is GenerationResult.Running -> {
                            LogBuffer.add("generate: running batch ${batchIndex + 1} node=${result.currentNode} progress=${result.progress}")
                            _uiState.update { state ->
                                state.copy(
                                    generationStatus = GenerationStatus.Running(
                                        currentNode = result.currentNode,
                                        progress = result.progress,
                                        currentBatch = batchIndex + 1,
                                        totalBatches = totalBatches
                                    )
                                )
                            }
                        }
                        is GenerationResult.Preview -> {
                            LogBuffer.add("generate: preview received batch ${batchIndex + 1}")
                            _uiState.update { it.copy(preview = result.image) }
                        }
                        is GenerationResult.Completed -> {
                            LogBuffer.add("generate: completed batch ${batchIndex + 1}, outputs=${result.outputs.size}")
                            _uiState.update { state ->
                                state.copy(
                                    generationStatus = if (batchIndex == totalBatches - 1) {
                                        GenerationStatus.Completed("")
                                    } else {
                                        GenerationStatus.Running(
                                            currentBatch = batchIndex + 1,
                                            totalBatches = totalBatches
                                        )
                                    },
                                    outputs = state.outputs + result.outputs
                                )
                            }
                            randomizeAllSeeds()
                        }
                        is GenerationResult.Error -> {
                            LogBuffer.add("generate: error batch ${batchIndex + 1}: ${result.message}")
                            _uiState.update {
                                it.copy(
                                    generationStatus = GenerationStatus.Error(result.message),
                                    errorMessage = result.message
                                )
                            }
                            batchFailed = true
                        }
                    }
                }

                if (batchFailed) break
            }

            LogBuffer.add("generate: batch loop finished, total outputs=${_uiState.value.outputs.size}")
        }
    }

    fun loadOptions(parameter: EditableParameter) {
        fetchOptions(parameter, forceRefresh = false)
    }

    fun refreshOptions(parameter: EditableParameter) {
        fetchOptions(parameter, forceRefresh = true)
    }

    private fun fetchOptions(parameter: EditableParameter, forceRefresh: Boolean) {
        val optionType = parameter.type as? FieldType.OptionType ?: return
        val optionKind = optionType.optionKind
        val (nodeClass, fieldName) = resolveOptionSource(optionKind) ?: return
        val key = ParameterKey(parameter.nodeId, parameter.path)
        val serverUrl = _uiState.value.serverUrl

        if (serverUrl.isBlank()) {
            LogBuffer.add("viewModel.fetchOptions: server URL empty")
            return
        }
        if (_uiState.value.optionLoading.contains(key)) return
        if (!forceRefresh && _uiState.value.optionLists.containsKey(key)) return

        _uiState.update { it.copy(optionLoading = it.optionLoading + key) }
        viewModelScope.launch {
            LogBuffer.add("viewModel.fetchOptions: $serverUrl/object_info/$nodeClass")
            val result = repository.fetchObjectInfo(serverUrl, nodeClass)
            val options = if (result.isSuccess) {
                extractOptions(result.getOrThrow(), nodeClass, fieldName).also {
                    LogBuffer.add("viewModel.fetchOptions: ${it.size} options")
                }
            } else {
                LogBuffer.add("viewModel.fetchOptions: ${result.exceptionOrNull()?.message}")
                emptyList()
            }
            _uiState.update {
                it.copy(
                    optionLists = it.optionLists + (key to options),
                    optionLoading = it.optionLoading - key
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
