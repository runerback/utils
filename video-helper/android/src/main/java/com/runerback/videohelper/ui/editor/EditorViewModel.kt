package com.runerback.videohelper.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.videohelper.data.api.EditStateDto
import com.runerback.videohelper.data.api.ExportEstimateResponseDto
import com.runerback.videohelper.data.api.RenderPartDto
import com.runerback.videohelper.data.api.RenderResponseDto
import com.runerback.videohelper.data.api.VideoHelperApi
import com.runerback.videohelper.data.api.VideoMetadataDto
import com.runerback.videohelper.data.export.ExportDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val JOB_POLL_INTERVAL_MS = 1000L
private const val JOB_POLL_TIMEOUT_MS = 30 * 60 * 1000L

data class EditorUiState(
    val loading: Boolean = true,
    val projectId: String = "",
    val metadata: VideoMetadataDto? = null,
    val state: EditStateDto = EditStateDto(),
    /** URL currently loaded in the player (original or latest preview). */
    val playerUrl: String? = null,
    val previewParts: List<RenderPartDto> = emptyList(),
    /** Set while a background job runs: "preview", "export", "export_gif". */
    val runningJob: String? = null,
    val exportResult: RenderResponseDto? = null,
    val gifEstimate: ExportEstimateResponseDto? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: VideoHelperApi,
    private val downloader: ExportDownloader,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(EditorUiState(projectId = projectId))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val project = api.getProject(projectId)
                _uiState.update {
                    it.copy(
                        loading = false,
                        metadata = project.metadata,
                        state = project.state,
                        playerUrl = api.mediaUrl(project.originalUrl),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "Failed to load project") }
            }
        }
    }

    // ---------- state editing ----------

    fun updateState(transform: (EditStateDto) -> EditStateDto) {
        _uiState.update { it.copy(state = transform(it.state), exportResult = null, gifEstimate = null) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            try {
                api.updateState(projectId, _uiState.value.state)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Save failed: ${e.message}") }
            }
        }
    }

    fun toggleClipSelection(index: Int) {
        updateState { state ->
            val current = state.sceneSplit.selectedClipIndexes
            val next = if (index in current) current - index else (current + index).sorted()
            state.copy(sceneSplit = state.sceneSplit.copy(selectedClipIndexes = next))
        }
    }

    // ---------- preview ----------

    fun renderPreview() {
        runRenderJob(kind = "preview") {
            api.createPreviewJob(projectId).jobId
        }
    }

    // ---------- export ----------

    fun exportMp4() {
        runRenderJob(kind = "export") {
            api.createExportJob(projectId).jobId
        }
    }

    fun exportGif() {
        runRenderJob(kind = "export_gif") {
            api.createExportGifJob(projectId).jobId
        }
    }

    private fun runRenderJob(kind: String, createJob: suspend () -> String) {
        if (_uiState.value.runningJob != null) return
        viewModelScope.launch {
            saveJob?.join() // make sure latest state is on the server before rendering
            _uiState.update { it.copy(runningJob = kind, error = null, message = null) }
            try {
                val jobId = createJob()
                val result = pollJob(jobId)
                when (kind) {
                    "preview" -> applyPreviewResult(result)
                    else -> _uiState.update {
                        it.copy(runningJob = null, exportResult = result, message = "Export finished")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(runningJob = null, error = e.message ?: "Job failed") }
            }
        }
    }

    private suspend fun pollJob(jobId: String): RenderResponseDto {
        val deadline = System.currentTimeMillis() + JOB_POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val job = api.getJob(jobId)
            when (job.status) {
                "done" -> return job.result ?: error("Job finished without a result")
                "error" -> error(job.error ?: "Render failed")
            }
            delay(JOB_POLL_INTERVAL_MS)
        }
        error("Timed out waiting for render job")
    }

    private suspend fun applyPreviewResult(result: RenderResponseDto) {
        val firstUrl = result.parts.firstOrNull()?.outputUrl ?: result.outputUrl
        _uiState.update {
            it.copy(
                runningJob = null,
                previewParts = result.parts,
                playerUrl = firstUrl?.let { url -> api.mediaUrl(url) } ?: it.playerUrl,
                message = if (result.parts.size > 1) "Preview: ${result.parts.size} clips" else null,
            )
        }
    }

    // ---------- gif estimate ----------

    fun estimateGif() {
        viewModelScope.launch {
            saveJob?.join()
            try {
                val estimate = api.estimateGif(projectId)
                _uiState.update { it.copy(gifEstimate = estimate, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Estimate failed: ${e.message}") }
            }
        }
    }

    // ---------- download ----------

    fun downloadExport() {
        val result = _uiState.value.exportResult ?: return
        viewModelScope.launch {
            try {
                val ext = if (result.format == "gif") "gif" else "mp4"
                val baseName = "video-helper_${projectId.take(8)}"
                if (result.parts.isNotEmpty()) {
                    for (part in result.parts) {
                        downloader.download(part.outputUrl, "${baseName}_part%03d.$ext".format(part.index))
                    }
                    _uiState.update { it.copy(message = "Downloading ${result.parts.size} files to Movies") }
                } else {
                    val path = result.outputUrl ?: api.exportDownloadPath(projectId)
                    downloader.download(path, "$baseName.$ext")
                    _uiState.update { it.copy(message = "Downloading to Movies") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Download failed: ${e.message}") }
            }
        }
    }

    fun playOriginal() {
        viewModelScope.launch {
            try {
                val project = api.getProject(projectId)
                _uiState.update { it.copy(playerUrl = api.mediaUrl(project.originalUrl)) }
            } catch (_: Exception) {
                // keep current player source
            }
        }
    }

    fun playPreviewPart(part: RenderPartDto) {
        viewModelScope.launch {
            _uiState.update { it.copy(playerUrl = api.mediaUrl(part.outputUrl)) }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, error = null) }
}
