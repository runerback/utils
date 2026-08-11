package com.runerback.comfyuiapi.ui.schemagenerator

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.comfyuiapi.data.model.SchemaFieldRole
import com.runerback.comfyuiapi.data.model.SchemaFieldSelection
import com.runerback.comfyuiapi.data.model.SchemaFieldType
import com.runerback.comfyuiapi.data.model.SchemaGeneratorUiState
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.data.model.detectSchemaFieldRole
import com.runerback.comfyuiapi.data.model.detectSchemaFieldType
import com.runerback.comfyuiapi.data.repository.ComfyRepository
import com.runerback.comfyuiapi.data.repository.LoadResult
import com.runerback.comfyuiapi.domain.buildSchema
import com.runerback.comfyuiapi.ui.components.LogBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@HiltViewModel
class SchemaGeneratorViewModel @Inject constructor(
    private val repository: ComfyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchemaGeneratorUiState())
    val uiState: StateFlow<SchemaGeneratorUiState> = _uiState.asStateFlow()

    fun loadWorkflow(uri: Uri) {
        LogBuffer.add("schemaGenerator.loadWorkflow: $uri")
        viewModelScope.launch {
            val result = repository.loadWorkflow(uri)
            when (result) {
                is LoadResult.Success -> {
                    val workflow = result.value
                    val selections = buildSelections(workflow)
                    _uiState.update {
                        it.copy(
                            workflowName = result.name,
                            workflow = workflow,
                            selections = selections,
                            errorMessage = null,
                            exportedUri = null
                        )
                    }
                    LogBuffer.add("schemaGenerator.loadWorkflow: ${selections.size} fields")
                }
                is LoadResult.Error -> {
                    LogBuffer.add("schemaGenerator.loadWorkflow error: ${result.message}")
                    _uiState.update {
                        it.copy(
                            errorMessage = result.message,
                            workflow = null,
                            selections = emptyList()
                        )
                    }
                }
            }
        }
    }

    fun toggleSelection(nodeId: String, fieldName: String) {
        updateSelection(nodeId, fieldName) { it.copy(selected = !it.selected) }
    }

    fun updateType(nodeId: String, fieldName: String, type: SchemaFieldType) {
        updateSelection(nodeId, fieldName) { it.copy(type = type) }
    }

    fun updateRole(nodeId: String, fieldName: String, role: SchemaFieldRole) {
        updateSelection(nodeId, fieldName) { it.copy(role = role) }
    }

    fun updateUploadType(nodeId: String, fieldName: String, uploadType: String) {
        updateSelection(nodeId, fieldName) { it.copy(uploadType = uploadType) }
    }

    fun updateMimeType(nodeId: String, fieldName: String, mimeType: String) {
        updateSelection(nodeId, fieldName) { it.copy(mimeType = mimeType) }
    }

    fun updateMin(nodeId: String, fieldName: String, min: Long?) {
        updateSelection(nodeId, fieldName) { it.copy(min = min) }
    }

    fun updateMax(nodeId: String, fieldName: String, max: Long?) {
        updateSelection(nodeId, fieldName) { it.copy(max = max) }
    }

    fun updateOrder(nodeId: String, fieldName: String, order: Int) {
        updateSelection(nodeId, fieldName) { it.copy(order = order) }
    }

    fun updateMultiline(nodeId: String, fieldName: String, multiline: Boolean) {
        updateSelection(nodeId, fieldName) { it.copy(multiline = multiline) }
    }

    fun exportSchema(uri: Uri) {
        val selections = _uiState.value.selections
        LogBuffer.add("schemaGenerator.exportSchema: ${selections.count { it.selected }} selected")
        viewModelScope.launch {
            val schema = buildSchema(selections)
            val result = repository.saveSchema(uri, schema)
            if (result.isSuccess) {
                _uiState.update { it.copy(exportedUri = uri, errorMessage = null) }
                LogBuffer.add("schemaGenerator.exportSchema: saved to $uri")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to save schema"
                _uiState.update { it.copy(errorMessage = msg) }
                LogBuffer.add("schemaGenerator.exportSchema: $msg")
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissExported() {
        _uiState.update { it.copy(exportedUri = null) }
    }

    private fun updateSelection(
        nodeId: String,
        fieldName: String,
        transform: (SchemaFieldSelection) -> SchemaFieldSelection
    ) {
        _uiState.update { state ->
            state.copy(
                selections = state.selections.map {
                    if (it.nodeId == nodeId && it.fieldName == fieldName) transform(it) else it
                }
            )
        }
    }

    private fun buildSelections(workflow: Workflow): List<SchemaFieldSelection> {
        var order = 0
        val selections = mutableListOf<SchemaFieldSelection>()
        for ((nodeId, node) in workflow) {
            val nodeLabel = node._meta?.title?.takeIf { it.isNotBlank() } ?: node.class_type
            for ((fieldName, value) in node.inputs) {
                if (value !is JsonPrimitive) continue
                val type = detectSchemaFieldType(value)
                val role = detectSchemaFieldRole(fieldName, value)
                selections.add(
                    SchemaFieldSelection(
                        nodeId = nodeId,
                        nodeLabel = nodeLabel,
                        fieldName = fieldName,
                        currentValue = value,
                        selected = false,
                        type = type,
                        role = role,
                        order = order++
                    )
                )
            }
        }
        return selections.sortedWith(compareBy({ it.nodeId }, { it.fieldName }))
    }
}
