package com.runerback.comfyuiapi.domain

import com.runerback.comfyuiapi.data.model.SchemaFieldRole
import com.runerback.comfyuiapi.data.model.SchemaFieldSelection
import com.runerback.comfyuiapi.data.model.SchemaFieldType
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.data.model.detectSchemaFieldRole
import com.runerback.comfyuiapi.data.model.detectSchemaFieldType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

fun schemaToSelections(
    schema: JsonObject,
    workflow: Workflow
): List<SchemaFieldSelection> {
    val schemaProperties = schema["properties"] as? JsonObject ?: return emptyList()
    val schemaFields = mutableMapOf<Pair<String, String>, JsonObject>()

    for ((nodeId, nodeSchema) in schemaProperties) {
        if (nodeSchema !is JsonObject) continue
        val inputsSchema = nodeSchema["properties"]?.let { it as? JsonObject }?.get("inputs")
            ?.let { it as? JsonObject }?.get("properties")?.let { it as? JsonObject }
            ?: continue
        for ((fieldName, spec) in inputsSchema) {
            if (spec is JsonObject) {
                schemaFields[nodeId to fieldName] = spec
            }
        }
    }

    var order = 0
    val selections = mutableListOf<SchemaFieldSelection>()
    for ((nodeId, node) in workflow) {
        val nodeLabel = node._meta?.title?.takeIf { it.isNotBlank() } ?: node.class_type
        for ((fieldName, value) in node.inputs) {
            if (value !is JsonPrimitive) continue

            val spec = schemaFields[nodeId to fieldName]
            val selected = spec != null

            val type = spec?.let { resolveSchemaFieldType(it) } ?: detectSchemaFieldType(value)
            val role = spec?.let { resolveSchemaFieldRole(it) } ?: detectSchemaFieldRole(fieldName, value)

            selections.add(
                SchemaFieldSelection(
                    nodeId = nodeId,
                    nodeLabel = nodeLabel,
                    fieldName = fieldName,
                    currentValue = value,
                    selected = selected,
                    type = type,
                    role = role,
                    uploadType = spec?.get("uploadType")?.let { it as? JsonPrimitive }?.contentOrNull ?: "input",
                    mimeType = spec?.get("mimeType")?.let { it as? JsonPrimitive }?.contentOrNull ?: "*/*",
                    min = spec?.get("minimum")?.let { it as? JsonPrimitive }?.doubleOrNull,
                    max = spec?.get("maximum")?.let { it as? JsonPrimitive }?.doubleOrNull,
                    order = spec?.get("order")?.let { it as? JsonPrimitive }?.intOrNull ?: order++,
                    multiline = spec?.get("multiline")?.let { it as? JsonPrimitive }?.booleanOrNull ?: false,
                    optionKind = spec?.get("optionKind")?.let { it as? JsonPrimitive }?.contentOrNull ?: "",
                    precision = spec?.get("precision")?.let { it as? JsonPrimitive }?.intOrNull?.coerceIn(0, 2) ?: 0
                )
            )
        }
    }

    return selections.sortedWith(compareBy({ it.order }, { it.nodeId }, { it.fieldName }))
}

private fun resolveSchemaFieldType(spec: JsonObject): SchemaFieldType {
    return when (spec["type"]?.let { it as? JsonPrimitive }?.contentOrNull) {
        SchemaFieldType.Integer.value -> SchemaFieldType.Integer
        SchemaFieldType.Number.value -> SchemaFieldType.Number
        else -> SchemaFieldType.String
    }
}

private fun resolveSchemaFieldRole(spec: JsonObject): SchemaFieldRole {
    return when (spec["role"]?.let { it as? JsonPrimitive }?.contentOrNull) {
        SchemaFieldRole.Upload.value -> SchemaFieldRole.Upload
        SchemaFieldRole.Seed.value -> SchemaFieldRole.Seed
        SchemaFieldRole.Option.value -> SchemaFieldRole.Option
        else -> SchemaFieldRole.None
    }
}
