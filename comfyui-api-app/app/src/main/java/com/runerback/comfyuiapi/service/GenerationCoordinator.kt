package com.runerback.comfyuiapi.service

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import com.runerback.comfyuiapi.data.model.GeneratedOutput
import com.runerback.comfyuiapi.data.model.GenerationStatus
import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.data.model.QueueState
import com.runerback.comfyuiapi.data.model.TaskItem
import com.runerback.comfyuiapi.data.model.TaskStatus
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.data.repository.ComfyRepository
import com.runerback.comfyuiapi.data.repository.GenerationResult
import com.runerback.comfyuiapi.ui.components.LogBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ComfyRepository
) {

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private val _generationStatus = MutableStateFlow<GenerationStatus>(GenerationStatus.Idle)
    val generationStatus: StateFlow<GenerationStatus> = _generationStatus.asStateFlow()

    private val _outputs = MutableStateFlow<List<GeneratedOutput>>(emptyList())
    val outputs: StateFlow<List<GeneratedOutput>> = _outputs.asStateFlow()

    private val _preview = MutableStateFlow<ImageBitmap?>(null)
    val preview: StateFlow<ImageBitmap?> = _preview.asStateFlow()

    private var serviceScope: CoroutineScope? = null
    private var runnerJob: Job? = null
    private var currentTaskJob: Job? = null

    private var currentServerUrl: String = ""
    private var currentWorkflow: Workflow? = null

    fun attachService(scope: CoroutineScope) {
        serviceScope = scope
        ensureRunner()
    }

    fun detachService() {
        serviceScope = null
        runnerJob?.cancel()
        runnerJob = null
        currentTaskJob = null
    }

    suspend fun enqueue(serverUrl: String, workflow: Workflow, snapshots: List<Map<ParameterKey, JsonElement>>) {
        currentServerUrl = serverUrl
        currentWorkflow = workflow

        _queueState.update { state ->
            val startIndex = state.nextIndex
            val newItems = snapshots.mapIndexed { offset, snapshot ->
                TaskItem(
                    id = UUID.randomUUID().toString(),
                    index = startIndex + offset,
                    valuesSnapshot = snapshot
                )
            }
            state.copy(
                items = state.items + newItems,
                nextIndex = startIndex + snapshots.size
            )
        }

        LogBuffer.add("coordinator.enqueue: ${snapshots.size} tasks, queue size=${_queueState.value.items.size}")
        startService()
        ensureRunner()
    }

    fun cancelCurrent() {
        LogBuffer.add("coordinator.cancelCurrent")
        currentTaskJob?.cancel()
        val url = currentServerUrl
        serviceScope?.launch {
            if (url.isNotBlank()) {
                repository.cancelGeneration(url)
            }
        }
    }

    fun cancelAll() {
        LogBuffer.add("coordinator.cancelAll")
        runnerJob?.cancel()
        runnerJob = null
        currentTaskJob?.cancel()
        currentTaskJob = null
        val url = currentServerUrl
        serviceScope?.launch {
            if (url.isNotBlank()) {
                repository.cancelGeneration(url)
            }
        }
        _queueState.update { QueueState() }
        _generationStatus.update { GenerationStatus.Cancelled }
        _preview.update { null }
    }

    fun cancelQueued(ids: List<String>) {
        if (ids.isEmpty()) return
        LogBuffer.add("coordinator.cancelQueued: ${ids.size} tasks")
        _queueState.update { state ->
            state.copy(items = state.items.filterNot { it.id in ids && it.status == TaskStatus.Queued })
        }
    }

    fun cancelAllQueued() {
        LogBuffer.add("coordinator.cancelAllQueued")
        _queueState.update { state ->
            state.copy(items = state.items.filter { it.status != TaskStatus.Queued })
        }
    }

    fun clearQueue() {
        LogBuffer.add("coordinator.clearQueue")
        runnerJob?.cancel()
        runnerJob = null
        currentTaskJob?.cancel()
        currentTaskJob = null
        _queueState.update { QueueState() }
        _generationStatus.update { GenerationStatus.Idle }
        _preview.update { null }
    }

    private fun startService() {
        val intent = Intent(context, ComfyGenerationService::class.java).apply {
            action = ComfyGenerationService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun ensureRunner() {
        val scope = serviceScope ?: return
        if (runnerJob?.isActive == true) return

        runnerJob = scope.launch {
            while (isActive) {
                val next = _queueState.value.items.firstOrNull { it.status == TaskStatus.Queued }
                if (next == null) {
                    _generationStatus.update { GenerationStatus.Idle }
                    break
                }
                runTask(next)
            }
            runnerJob = null
        }
    }

    private suspend fun runTask(task: TaskItem) {
        val scope = serviceScope ?: return
        val workflow = currentWorkflow ?: return
        val serverUrl = currentServerUrl
        val patched = repository.patchWorkflow(workflow, task.valuesSnapshot)
        LogBuffer.add("coordinator.runTask: starting task #${task.index}")

        var completed = false
        var failedMessage: String? = null

        val flowJob = scope.launch {
            try {
                repository.generate(serverUrl, patched).collect { result ->
                    LogBuffer.add("coordinator.runTask: task #${task.index} event=$result")
                    when (result) {
                        is GenerationResult.Connecting -> {
                            _generationStatus.update {
                                GenerationStatus.Running(
                                    currentQueueIndex = task.index,
                                    queueSize = _queueState.value.items.size
                                )
                            }
                        }
                        is GenerationResult.Running -> {
                            _generationStatus.update {
                                GenerationStatus.Running(
                                    currentNode = result.currentNode,
                                    progress = result.progress,
                                    currentQueueIndex = task.index,
                                    queueSize = _queueState.value.items.size
                                )
                            }
                            updateQueueItem(task.id) { it.copy(progress = result.progress) }
                        }
                        is GenerationResult.Preview -> {
                            _preview.update { result.image }
                        }
                        is GenerationResult.Completed -> {
                            _outputs.update { it + result.outputs }
                            completed = true
                        }
                        is GenerationResult.Error -> {
                            failedMessage = result.message
                        }
                    }
                }
            } catch (e: Exception) {
                LogBuffer.add("coordinator.runTask: task #${task.index} exception ${e.message}")
                failedMessage = e.message ?: "Task failed"
            }
        }

        currentTaskJob = flowJob
        updateQueueItem(task.id) { it.copy(status = TaskStatus.Running) }
        flowJob.join()
        currentTaskJob = null

        val finalStatus = when {
            flowJob.isCancelled -> TaskStatus.Cancelled
            failedMessage != null -> TaskStatus.Failed(failedMessage)
            completed -> TaskStatus.Completed
            else -> TaskStatus.Failed("Task ended unexpectedly")
        }
        updateQueueItem(task.id) { it.copy(status = finalStatus, progress = null) }
        LogBuffer.add("coordinator.runTask: task #${task.index} finished with status=$finalStatus")
    }

    private fun updateQueueItem(id: String, transform: (TaskItem) -> TaskItem) {
        _queueState.update { state ->
            val newItems = state.items.map { item ->
                if (item.id == id) transform(item) else item
            }
            state.copy(items = newItems)
        }
    }
}
