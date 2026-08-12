package com.runerback.comfyuiapi.domain

import com.runerback.comfyuiapi.data.model.EditableParameter
import com.runerback.comfyuiapi.data.model.FieldType
import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.ui.components.LogBuffer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchemaParser @Inject constructor() {

    fun parse(schema: JsonObject, workflow: Workflow): List<EditableParameter> {
        LogBuffer.add("schemaParser.parse: schema keys=${schema.keys.joinToString()}")
        val result = mutableListOf<EditableParameter>()
        val rootProperties = schema["properties"] as? JsonObject ?: run {
            LogBuffer.add("schemaParser.parse: no root properties")
            return emptyList()
        }

        for ((nodeId, nodeSchema) in rootProperties) {
            if (nodeSchema !is JsonObject) continue
            val nodeLabel = workflow[nodeId]?.let { node ->
                node._meta?.title?.takeIf { it.isNotBlank() } ?: node.class_type
            } ?: nodeId

            collectLeaves(
                nodeId = nodeId,
                nodeLabel = nodeLabel,
                currentPath = emptyList(),
                schemaObj = nodeSchema,
                accumulator = result
            )
        }

        val filtered = result
            .sortedWith(compareBy({ it.order }, { it.nodeId }, { it.fieldName }))
            .filter { param ->
                val value = resolveValue(workflow, param.nodeId, param.path)
                LogBuffer.add("schemaParser.parse: ${param.nodeId}/${param.fieldName} value=${value != null}")
                value != null
            }
        LogBuffer.add("schemaParser.parse: returning ${filtered.size} parameters")
        return filtered
    }

    private fun collectLeaves(
        nodeId: String,
        nodeLabel: String,
        currentPath: List<String>,
        schemaObj: JsonObject,
        accumulator: MutableList<EditableParameter>
    ) {
        val properties = schemaObj["properties"] as? JsonObject ?: return

        for ((name, spec) in properties) {
            if (spec !is JsonObject) continue

            val childPath = currentPath + name
            val childProperties = spec["properties"] as? JsonObject

            if (childProperties != null) {
                collectLeaves(
                    nodeId = nodeId,
                    nodeLabel = nodeLabel,
                    currentPath = childPath,
                    schemaObj = spec,
                    accumulator = accumulator
                )
            } else {
                val leafName = childPath.last()
                val type = resolveType(leafName, spec)
                accumulator.add(
                    EditableParameter(
                        nodeId = nodeId,
                        nodeLabel = nodeLabel,
                        fieldName = leafName,
                        path = childPath,
                        type = type,
                        min = (spec["minimum"] as? JsonPrimitive)?.longOrNull,
                        max = (spec["maximum"] as? JsonPrimitive)?.longOrNull,
                        default = spec["default"],
                        order = (spec["order"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0,
                        multiline = (spec["multiline"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false,
                        precision = (spec["precision"] as? JsonPrimitive)?.content?.toIntOrNull()?.coerceIn(0, 2) ?: 0
                    )
                )
            }
        }
    }

    private fun resolveType(fieldName: String, spec: JsonObject): FieldType {
        val type = (spec["type"] as? JsonPrimitive)?.content
        val role = (spec["role"] as? JsonPrimitive)?.content
        val optionKind = (spec["optionKind"] as? JsonPrimitive)?.content
        val randomize = (spec["randomize"] as? JsonPrimitive)?.content
        val uploadType = (spec["uploadType"] as? JsonPrimitive)?.content ?: "input"
        val mimeType = (spec["mimeType"] as? JsonPrimitive)?.content ?: "*/*"
        val precision = (spec["precision"] as? JsonPrimitive)?.content?.toIntOrNull()?.coerceIn(0, 2) ?: 0

        return when {
            role == "option" && optionKind != null && resolveOptionSource(optionKind) != null -> {
                LogBuffer.add("schemaParser.resolveType: option $optionKind")
                FieldType.OptionType(optionKind)
            }
            role == "upload" || role == "upload_image" -> FieldType.UploadType(uploadType, mimeType)
            role == "seed" || randomize != null -> FieldType.SeedType
            fieldName == "width" || fieldName == "height" -> FieldType.DimensionType
            type == "string" -> FieldType.StringType
            type == "integer" || type == "number" -> FieldType.IntType(precision)
            else -> FieldType.StringType
        }
    }
}

fun extractOptions(objectInfo: JsonObject, nodeClass: String, fieldName: String): List<String> {
    val nodeDef = objectInfo[nodeClass]?.jsonObject ?: return emptyList()
    val inputDef = nodeDef["input"]?.jsonObject ?: return emptyList()

    val required = inputDef["required"]?.jsonObject
    val optional = inputDef["optional"]?.jsonObject

    return extractOptionsFromGroup(required, fieldName)
        .ifEmpty { extractOptionsFromGroup(optional, fieldName) }
}

private fun extractOptionsFromGroup(group: JsonObject?, fieldName: String): List<String> {
    val fieldDef = group?.get(fieldName)?.jsonArray ?: return emptyList()

    // Newer ComfyUI nodes: ["COMBO", {"options": [...]}]
    fieldDef.getOrNull(1)?.jsonObject?.get("options")?.jsonArray?.let {
        return it.mapNotNull { item -> item.jsonPrimitive.content }
    }

    // Older format: [["option1", "option2", ...]]
    fieldDef.firstOrNull()?.jsonArray?.let {
        return it.mapNotNull { item -> item.jsonPrimitive.content }
    }

    return emptyList()
}

fun resolveValue(workflow: Workflow, nodeId: String, path: List<String>): JsonElement? {
    val node = workflow[nodeId] ?: return null
    var current: JsonElement = node.inputs
    for (segment in path) {
        if (segment == "inputs") {
            current = node.inputs
        } else {
            current = (current as? JsonObject)?.get(segment) ?: return null
        }
    }
    return current
}

fun resolveValue(workflow: Workflow, key: ParameterKey): JsonElement? {
    return resolveValue(workflow, key.nodeId, key.path)
}
