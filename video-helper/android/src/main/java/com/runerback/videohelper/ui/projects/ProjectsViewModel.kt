package com.runerback.videohelper.ui.projects

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.videohelper.data.api.ProjectListItemDto
import com.runerback.videohelper.data.api.VideoHelperApi
import com.runerback.videohelper.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val loading: Boolean = true,
    val uploading: Boolean = false,
    val configured: Boolean = true,
    val projects: List<ProjectListItemDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: VideoHelperApi,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val settings = settingsRepository.settings.first()
                if (!settings.isConfigured) {
                    _uiState.update { it.copy(loading = false, configured = false, projects = emptyList()) }
                    return@launch
                }
                val projects = api.listProjects()
                _uiState.update { it.copy(loading = false, configured = true, projects = projects) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "Failed to load projects") }
            }
        }
    }

    fun upload(uri: Uri, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true, error = null) }
            try {
                val fileName = queryDisplayName(uri) ?: "upload.mp4"
                val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read selected file")
                val created = api.uploadProject(fileName, bytes, mimeType)
                _uiState.update { it.copy(uploading = false) }
                onCreated(created.projectId)
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(uploading = false, error = e.message ?: "Upload failed") }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
}
