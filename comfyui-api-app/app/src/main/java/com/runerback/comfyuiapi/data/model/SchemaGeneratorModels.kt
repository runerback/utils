package com.runerback.comfyuiapi.data.model

import android.net.Uri
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

enum class SchemaFieldRole(val value: String?) {
    None(null),
    Upload("upload"),
    Seed("seed")
}

enum class SchemaFieldType(val value: String) {
    String("string"),
    Integer("integer"),
    Number("number")
}

data class SchemaFieldSelection(
    val nodeId: String,
    val nodeLabel: String,
    val fieldName: String,
    val currentValue: JsonElement,
    val selected: Boolean = false,
    val type: SchemaFieldType = SchemaFieldType.String,
    val role: SchemaFieldRole = SchemaFieldRole.None,
    val uploadType: String = "input",
    val mimeType: String = "*/*",
    val min: Long? = null,
    val max: Long? = null,
    val order: Int = 0
) {
    val path: List<String>
        get() = listOf("inputs", fieldName)
}

data class SchemaGeneratorUiState(
    val workflowName: String = "",
    val workflow: Workflow? = null,
    val selections: List<SchemaFieldSelection> = emptyList(),
    val errorMessage: String? = null,
    val exportedUri: Uri? = null
)

fun detectSchemaFieldType(value: JsonElement): SchemaFieldType {
    val primitive = value as? JsonPrimitive ?: return SchemaFieldType.String
    return when {
        primitive.content.toLongOrNull() != null -> SchemaFieldType.Integer
        primitive.content.toDoubleOrNull() != null -> SchemaFieldType.Number
        else -> SchemaFieldType.String
    }
}

fun detectSchemaFieldRole(fieldName: String, value: JsonElement): SchemaFieldRole {
    return when {
        fieldName.equals("image", ignoreCase = true) && value is JsonPrimitive && value.content.isNotBlank() -> SchemaFieldRole.Upload
        fieldName.equals("seed", ignoreCase = true) -> SchemaFieldRole.Seed
        else -> SchemaFieldRole.None
    }
}
