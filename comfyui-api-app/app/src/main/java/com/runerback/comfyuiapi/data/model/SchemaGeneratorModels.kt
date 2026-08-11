package com.runerback.comfyuiapi.data.model

import android.net.Uri
import com.runerback.comfyuiapi.domain.OPTION_KIND_SOURCES
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

enum class SchemaFieldRole(val value: String?) {
    None(null),
    Upload("upload"),
    Seed("seed"),
    Option("option")
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
    val order: Int = 0,
    val multiline: Boolean = false,
    val optionKind: String = ""
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
        detectOptionKind(fieldName).isNotBlank() -> SchemaFieldRole.Option
        else -> SchemaFieldRole.None
    }
}

fun detectOptionKind(fieldName: String): String {
    return OPTION_KIND_SOURCES.entries.find { it.value.second == fieldName }?.key ?: ""
}
