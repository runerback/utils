package com.runerback.comfyuiapi.data.model

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed class FieldType {
    data object StringType : FieldType()
    data class IntType(val precision: Int = 0) : FieldType()
    data object SeedType : FieldType()
    data object DimensionType : FieldType()
    data class UploadType(val uploadType: String, val mimeType: String) : FieldType()
    data class OptionType(val optionKind: String) : FieldType()
}

data class ParameterKey(val nodeId: String, val path: List<String>)

data class EditableParameter(
    val nodeId: String,
    val nodeLabel: String,
    val fieldName: String,
    val path: List<String>,
    val type: FieldType,
    val min: Double? = null,
    val max: Double? = null,
    val default: JsonElement? = null,
    val order: Int = 0,
    val multiline: Boolean = false,
    val precision: Int = 0
)

sealed class GenerationStatus {
    data object Idle : GenerationStatus()
    data object Connecting : GenerationStatus()
    data class Running(
        val currentNode: String? = null,
        val progress: Pair<Int, Int>? = null,
        val currentBatch: Int? = null,
        val totalBatches: Int? = null
    ) : GenerationStatus()
    data class Completed(val promptId: String) : GenerationStatus()
    data class Error(val message: String) : GenerationStatus()
}

data class UiState(
    val serverUrl: String = "",
    val serverUrlHistory: List<String> = emptyList(),
    val generationTimeoutMs: Long = 30000L,
    val workflowName: String = "",
    val schemaName: String = "",
    val hasWorkflow: Boolean = false,
    val hasSchema: Boolean = false,
    val parameters: List<EditableParameter> = emptyList(),
    val currentValues: Map<ParameterKey, JsonElement> = emptyMap(),
    val pendingUploads: Map<ParameterKey, android.net.Uri> = emptyMap(),
    val fixedSeeds: Set<ParameterKey> = emptySet(),
    val modifiedKeys: Set<ParameterKey> = emptySet(),
    val generationStatus: GenerationStatus = GenerationStatus.Idle,
    val preview: ImageBitmap? = null,
    val errorMessage: String? = null,
    val batchCount: Int = 1,
    val optionLists: Map<ParameterKey, List<String>> = emptyMap(),
    val optionLoading: Set<ParameterKey> = emptySet()
)

data class GeneratedOutput(
    val nodeId: String,
    val bitmap: ImageBitmap,
    val createdAt: Long = System.currentTimeMillis()
)
