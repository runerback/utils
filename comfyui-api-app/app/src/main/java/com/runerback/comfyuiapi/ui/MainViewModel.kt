package com.runerback.comfyuiapi.ui

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.comfyuiapi.data.model.EditableParameter
import com.runerback.comfyuiapi.data.model.FieldType
import com.runerback.comfyuiapi.data.model.GeneratedOutput
import com.runerback.comfyuiapi.data.model.GenerationStatus
import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.data.model.QueueState
import com.runerback.comfyuiapi.data.model.TaskItem
import com.runerback.comfyuiapi.data.model.TaskStatus
import com.runerback.comfyuiapi.data.model.UiState
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.data.repository.ComfyRepository
import com.runerback.comfyuiapi.data.repository.GenerationResult
import com.runerback.comfyuiapi.data.repository.LoadResult
import com.runerback.comfyuiapi.domain.SchemaParser
import com.runerback.comfyuiapi.domain.extractOptions
import com.runerback.comfyuiapi.domain.resolveOptionSource
import com.runerback.comfyuiapi.domain.resolveValue
import com.runerback.comfyuiapi.ui.components.LogBuffer
import com.runerback.comfyuiapi.ui.components.randomSeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ComfyRepository,
    private val schemaParser: SchemaParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _allOutputs = MutableStateFlow<List<GeneratedOutput>>(emptyList())
    val allOutputs: StateFlow<List<GeneratedOutput>> = _allOutputs.asStateFlow()

    var loadedWorkflow: Workflow? = null
        private set
    var loadedSchema: JsonObject? = null
        private set
    private var pendingSchemaUri: Uri? = null
    private var queueRunner: Job? = null

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

    fun saveSchemaDefaults(uri: Uri) {
        val schemaJson = loadedSchema ?: run {
            _uiState.update { it.copy(errorMessage = "Load a schema first") }
            return
        }
        val workflow = loadedWorkflow ?: run {
            _uiState.update { it.copy(errorMessage = "Load a workflow first") }
            return
        }
        LogBuffer.add("saveSchemaDefaults: $uri")
        viewModelScope.launch {
            val state = _uiState.value
            val result = repository.saveSchemaWithDefaults(
                uri = uri,
                schemaJson = schemaJson,
                workflow = workflow,
                parameters = state.parameters,
                currentValues = state.currentValues
            )
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Failed to save schema defaults"
                LogBuffer.add("saveSchemaDefaults error: $msg")
                _uiState.update { it.copy(errorMessage = msg) }
            } else {
                LogBuffer.add("saveSchemaDefaults success")
                _uiState.update { it.copy(schemaName = state.schemaName, errorMessage = null) }
            }
        }
    }

    fun loadWorkflow(uri: Uri) {
        LogBuffer.add("loadWorkflow: $uri")
        viewModelScope.launch {
            when (val result = repository.loadWorkflow(uri)) {
                is LoadResult.Success -> {
                    LogBuffer.add("loadWorkflow success: ${result.value.size} nodes")
                    loadedWorkflow = result.value
                    loadedSchema = null
                    _uiState.update {
                        it.copy(
                            workflowName = result.name,
                            hasWorkflow = true,
                            hasSchema = false,
                            parameters = emptyList(),
                            currentValues = emptyMap(),
                            preview = null,
                            errorMessage = null,
                            fixedSeeds = emptySet(),
                            modifiedKeys = emptySet(),
                            optionLists = emptyMap(),
                            optionLoading = emptySet()
                        )
                    }
                    pendingSchemaUri?.let { pending ->
                        LogBuffer.add("loadWorkflow: processing pending schema")
                        loadSchemaInternal(pending, result.value)
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
        val workflow = loadedWorkflow
        if (workflow == null) {
            pendingSchemaUri = uri
            LogBuffer.add("loadSchema: pending until workflow loaded")
            return
        }
        LogBuffer.add("loadSchema: $uri")
        loadSchemaInternal(uri, workflow)
    }

    private fun loadSchemaInternal(uri: Uri, workflow: Workflow) {
        viewModelScope.launch {
            when (val result = repository.loadSchema(uri, workflow)) {
                is LoadResult.Success -> {
                    LogBuffer.add("loadSchema success: ${result.value.parameters.size} parameters")
                    applyLoadedSchema(result.value.schemaJson, result.value.parameters, result.name)
                }
                is LoadResult.Error -> {
                    LogBuffer.add("loadSchema error: ${result.message}")
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun reloadSchema(schemaJson: JsonObject) {
        val workflow = loadedWorkflow ?: run {
            _uiState.update { it.copy(errorMessage = "Load a workflow first") }
            return
        }
        LogBuffer.add("reloadSchema")
        viewModelScope.launch {
            val parameters = try {
                schemaParser.parse(schemaJson, workflow)
            } catch (e: Exception) {
                LogBuffer.add("reloadSchema parse error: ${e.message}")
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to parse schema") }
                return@launch
            }
            if (parameters.isEmpty()) {
                LogBuffer.add("reloadSchema: no matching parameters")
                _uiState.update { it.copy(errorMessage = "Edited schema does not match the loaded workflow") }
                return@launch
            }
            applyLoadedSchema(schemaJson, parameters, _uiState.value.schemaName)
            LogBuffer.add("reloadSchema success: ${parameters.size} parameters")
        }
    }

    private fun applyLoadedSchema(schemaJson: JsonObject, parameters: List<EditableParameter>, name: String) {
        loadedSchema = schemaJson
        pendingSchemaUri = null
        val workflow = loadedWorkflow ?: return
        val values = repository.initialValues(workflow, parameters)
        val modifiedKeys = computeModifiedKeys(values)
        _uiState.update {
            it.copy(
                schemaName = name,
                hasSchema = true,
                parameters = parameters,
                currentValues = values,
                preview = null,
                errorMessage = null,
                fixedSeeds = emptySet(),
                modifiedKeys = modifiedKeys,
                optionLists = emptyMap(),
                optionLoading = emptySet(),
                pendingUploads = emptyMap(),
                multiInputEnabled = emptySet(),
                multiInputUris = emptyMap()
            )
        }
    }

    fun updateValue(parameter: EditableParameter, value: JsonElement) {
        _uiState.update { state ->
            val newValues = state.currentValues.toMutableMap().apply {
                put(ParameterKey(parameter.nodeId, parameter.path), value)
            }
            state.copy(
                currentValues = newValues,
                modifiedKeys = computeModifiedKeys(newValues)
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
                    newValues[ParameterKey(param.nodeId, param.path)] = JsonPrimitive(randomSeed(param.min, param.max))
                }
            state.copy(
                currentValues = newValues,
                modifiedKeys = computeModifiedKeys(newValues)
            )
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

    fun toggleMultiInput(parameter: EditableParameter) {
        val key = ParameterKey(parameter.nodeId, parameter.path)
        _uiState.update { state ->
            val enabled = state.multiInputEnabled.contains(key)
            state.copy(
                multiInputEnabled = if (enabled) state.multiInputEnabled - key else state.multiInputEnabled + key,
                multiInputUris = state.multiInputUris.toMutableMap().apply { remove(key) },
                pendingUploads = state.pendingUploads.toMutableMap().apply { remove(key) }
            )
        }
    }

    fun setMultiInputUris(parameter: EditableParameter, uris: List<Uri>) {
        val key = ParameterKey(parameter.nodeId, parameter.path)
        _uiState.update { state ->
            state.copy(
                multiInputUris = state.multiInputUris.toMutableMap().apply {
                    if (uris.isNotEmpty()) put(key, uris) else remove(key)
                }
            )
        }
    }

    fun removeMultiInputUri(parameter: EditableParameter, uri: Uri) {
        val key = ParameterKey(parameter.nodeId, parameter.path)
        _uiState.update { state ->
            val updated = state.multiInputUris[key]?.filter { it != uri }
            state.copy(
                multiInputUris = state.multiInputUris.toMutableMap().apply {
                    if (updated.isNullOrEmpty()) remove(key) else put(key, updated)
                }
            )
        }
    }

    fun clearMultiInputUris(parameter: EditableParameter) {
        val key = ParameterKey(parameter.nodeId, parameter.path)
        _uiState.update { state ->
            state.copy(
                multiInputUris = state.multiInputUris.toMutableMap().apply { remove(key) }
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
            _uiState.update { it.copy(errorMessage = null) }

            val initialState = _uiState.value
            val pending = initialState.pendingUploads
            val multiPending = initialState.multiInputUris
            LogBuffer.add("generate: pending uploads=${pending.size}, multi uploads=${multiPending.size}")

            val singleReplacements = mutableMapOf<ParameterKey, JsonElement>()
            val multiReplacements = mutableMapOf<ParameterKey, List<JsonElement>>()

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
                    singleReplacements[key] = JsonPrimitive(filename)
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

            for ((key, uris) in multiPending) {
                if (uris.isEmpty()) {
                    val msg = "No images selected for multi-input $key"
                    LogBuffer.add("generate: $msg")
                    _uiState.update {
                        it.copy(
                            generationStatus = GenerationStatus.Error(msg),
                            errorMessage = msg
                        )
                    }
                    return@launch
                }
                val param = initialState.parameters.find {
                    ParameterKey(it.nodeId, it.path) == key
                } ?: continue
                val uploadType = (param.type as? FieldType.UploadType)?.uploadType ?: "input"
                val filenames = mutableListOf<String>()
                for (uri in uris) {
                    LogBuffer.add("generate: uploading multi $key type=$uploadType uri=$uri")
                    val result = repository.uploadImage(serverUrl, uri, uploadType)
                    if (result.isSuccess) {
                        val filename = result.getOrThrow()
                        LogBuffer.add("generate: multi upload success filename=$filename")
                        filenames.add(filename)
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "Unknown upload error"
                        LogBuffer.add("generate: multi upload failed: $msg")
                        _uiState.update {
                            it.copy(
                                generationStatus = GenerationStatus.Error("Upload failed: $msg"),
                                errorMessage = msg
                            )
                        }
                        return@launch
                    }
                }
                multiReplacements[key] = filenames.map { JsonPrimitive(it) }
            }

            if (singleReplacements.isNotEmpty()) {
                _uiState.update { state ->
                    val newValues = state.currentValues.toMutableMap().apply {
                        putAll(singleReplacements)
                    }
                    state.copy(
                        currentValues = newValues,
                        modifiedKeys = computeModifiedKeys(newValues)
                    )
                }
            }

            val snapshots = mutableListOf<Map<ParameterKey, JsonElement>>()

            if (multiReplacements.isEmpty()) {
                repeat(totalBatches) { batchIndex ->
                    if (batchIndex > 0) {
                        randomizeAllSeeds()
                    }
                    snapshots.add(_uiState.value.currentValues.toMap())
                }
            } else {
                val multiKeys = multiReplacements.keys.toList()
                val multiLists = multiKeys.map { multiReplacements.getValue(it) }
                val product = cartesianProduct(multiLists)
                var firstSnapshot = true
                repeat(totalBatches) {
                    for (combo in product) {
                        if (!firstSnapshot) {
                            randomizeAllSeeds()
                        }
                        firstSnapshot = false
                        val snapshot = _uiState.value.currentValues.toMutableMap().apply {
                            for ((index, key) in multiKeys.withIndex()) {
                                put(key, combo[index])
                            }
                        }
                        snapshots.add(snapshot)
                    }
                }
            }

            _uiState.update { state ->
                val startIndex = state.queue.nextIndex
                val newItems = snapshots.mapIndexed { offset, snapshot ->
                    TaskItem(
                        id = UUID.randomUUID().toString(),
                        index = startIndex + offset,
                        valuesSnapshot = snapshot
                    )
                }
                state.copy(
                    queue = state.queue.copy(
                        items = state.queue.items + newItems,
                        nextIndex = startIndex + snapshots.size
                    )
                )
            }

            LogBuffer.add("generate: enqueued ${snapshots.size} tasks, queue size=${_uiState.value.queue.items.size}")
            startQueueRunnerIfNeeded()
        }
    }

    private fun startQueueRunnerIfNeeded() {
        if (queueRunner?.isActive == true) return
        queueRunner = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val next = state.queue.items.firstOrNull { it.status == TaskStatus.Queued }
                if (next == null) {
                    _uiState.update { state ->
                        state.copy(generationStatus = GenerationStatus.Idle)
                    }
                    break
                }
                runTask(next)
            }
            queueRunner = null
        }
    }

    private suspend fun CoroutineScope.runTask(task: TaskItem) {
        val workflow = loadedWorkflow ?: return
        val serverUrl = _uiState.value.serverUrl
        val patched = repository.patchWorkflow(workflow, task.valuesSnapshot)
        LogBuffer.add("runTask: starting task #${task.index}")

        var completed = false
        var failedMessage: String? = null

        val flowJob = launch {
            try {
                repository.generate(serverUrl, patched).collect { result ->
                    when (result) {
                        is GenerationResult.Connecting -> {
                            _uiState.update { state ->
                                state.copy(
                                    generationStatus = GenerationStatus.Running(
                                        currentQueueIndex = task.index,
                                        queueSize = state.queue.items.size
                                    )
                                )
                            }
                        }
                        is GenerationResult.Running -> {
                            LogBuffer.add("runTask: task #${task.index} node=${result.currentNode} progress=${result.progress}")
                            _uiState.update { state ->
                                state.copy(
                                    generationStatus = GenerationStatus.Running(
                                        currentNode = result.currentNode,
                                        progress = result.progress,
                                        currentQueueIndex = task.index,
                                        queueSize = state.queue.items.size
                                    ),
                                    queue = state.queue.copy(
                                        items = state.queue.items.map { item ->
                                            if (item.id == task.id) {
                                                item.copy(progress = result.progress)
                                            } else item
                                        }
                                    )
                                )
                            }
                        }
                        is GenerationResult.Preview -> {
                            _uiState.update { it.copy(preview = result.image) }
                        }
                        is GenerationResult.Completed -> {
                            _allOutputs.update { it + result.outputs }
                            completed = true
                        }
                        is GenerationResult.Error -> {
                            failedMessage = result.message
                        }
                    }
                }
            } catch (e: Exception) {
                failedMessage = e.message ?: "Task failed"
            }
        }

        updateQueueItem(task.id) { it.copy(status = TaskStatus.Running, job = flowJob) }

        flowJob.join()

        val finalStatus = when {
            flowJob.isCancelled -> TaskStatus.Cancelled
            failedMessage != null -> TaskStatus.Failed(failedMessage!!)
            completed -> TaskStatus.Completed
            else -> TaskStatus.Failed("Task ended unexpectedly")
        }
        updateQueueItem(task.id) { it.copy(status = finalStatus, progress = null, job = null) }
        LogBuffer.add("runTask: task #${task.index} finished with status=$finalStatus")
    }

    private fun updateQueueItem(id: String, transform: (TaskItem) -> TaskItem) {
        _uiState.update { state ->
            val newItems = state.queue.items.map { item ->
                if (item.id == id) transform(item) else item
            }
            state.copy(queue = state.queue.copy(items = newItems))
        }
    }

    fun cancelCurrentTask() {
        val serverUrl = _uiState.value.serverUrl
        val running = _uiState.value.queue.items.firstOrNull { it.status == TaskStatus.Running }
        if (running == null) return
        LogBuffer.add("cancelCurrentTask: task #${running.index}")
        running.job?.cancel()
        viewModelScope.launch {
            if (serverUrl.isNotBlank()) {
                repository.cancelGeneration(serverUrl)
            }
        }
    }

    fun cancelQueuedTasks(ids: List<String>) {
        if (ids.isEmpty()) return
        LogBuffer.add("cancelQueuedTasks: ${ids.size} tasks")
        _uiState.update { state ->
            val newItems = state.queue.items.filterNot { it.id in ids && it.status == TaskStatus.Queued }
            state.copy(queue = state.queue.copy(items = newItems))
        }
    }

    fun cancelAllQueued() {
        LogBuffer.add("cancelAllQueued")
        _uiState.update { state ->
            val newItems = state.queue.items.filter { it.status != TaskStatus.Queued }
            state.copy(queue = state.queue.copy(items = newItems))
        }
    }

    fun cancelAll() {
        val serverUrl = _uiState.value.serverUrl
        LogBuffer.add("cancelAll")
        queueRunner?.cancel()
        queueRunner = null
        viewModelScope.launch {
            if (serverUrl.isNotBlank()) {
                repository.cancelGeneration(serverUrl)
            }
            _uiState.update { state ->
                state.copy(
                    queue = QueueState(),
                    generationStatus = GenerationStatus.Cancelled,
                    errorMessage = null
                )
            }
        }
    }

    fun clearQueue() {
        LogBuffer.add("clearQueue")
        _uiState.update { state ->
            state.copy(
                queue = QueueState(),
                generationStatus = GenerationStatus.Idle,
                errorMessage = null
            )
        }
    }

    fun saveOutputToDownloads(
        output: GeneratedOutput,
        onResult: (success: Boolean, message: String) -> Unit = { _, _ -> }
    ) {
        LogBuffer.add("saveOutputToDownloads: ${output.filename}")
        viewModelScope.launch {
            val result = repository.saveOutputToDownloads(output)
            if (result.isSuccess) {
                LogBuffer.add("saveOutputToDownloads: saved ${output.filename}")
                onResult(true, "Saved ${output.filename}")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to save ${output.filename}"
                LogBuffer.add("saveOutputToDownloads: error $msg")
                _uiState.update { it.copy(errorMessage = msg) }
                onResult(false, msg)
            }
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

    private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return listOf(emptyList())
        var result = listOf(emptyList<T>())
        for (list in lists) {
            result = result.flatMap { prefix -> list.map { prefix + it } }
        }
        return result
    }

    private fun computeModifiedKeys(currentValues: Map<ParameterKey, JsonElement>): Set<ParameterKey> {
        val workflow = loadedWorkflow ?: return emptySet()
        return currentValues.entries.mapNotNull { (key, value) ->
            if (value != resolveValue(workflow, key)) key else null
        }.toSet()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
