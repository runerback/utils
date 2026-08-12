package com.runerback.comfyuiapi.domain

import com.runerback.comfyuiapi.data.model.EditableParameter
import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.data.model.Workflow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

fun applyDefaultsToSchema(
    schema: JsonObject,
    parameters: List<EditableParameter>,
    workflow: Workflow,
    currentValues: Map<ParameterKey, JsonElement>
): JsonObject {
    val keysToUpdate = parameters.mapNotNull { param ->
        val key = ParameterKey(param.nodeId, param.path)
        val current = currentValues[key] ?: return@mapNotNull null
        val original = resolveValue(workflow, key)
        if (current != original) key to current else null
    }.toMap()

    if (keysToUpdate.isEmpty()) return schema

    val rootProperties = schema["properties"] as? JsonObject ?: return schema
    val updatedProperties = buildJsonObject {
        for ((nodeId, nodeSchema) in rootProperties) {
            if (nodeSchema !is JsonObject) {
                put(nodeId, nodeSchema)
                continue
            }
            putJsonObject(nodeId) {
                for ((key, value) in nodeSchema) {
                    if (key == "properties" && value is JsonObject) {
                        putJsonObject("properties") {
                            for ((segment, segmentSchema) in value) {
                                put(segment, updateSchemaSegment(segmentSchema, nodeId, listOf(segment), keysToUpdate))
                            }
                        }
                    } else {
                        put(key, value)
                    }
                }
            }
        }
    }

    return JsonObject(schema.toMutableMap().apply { put("properties", updatedProperties) })
}

private fun updateSchemaSegment(
    segmentSchema: JsonElement,
    nodeId: String,
    path: List<String>,
    defaults: Map<ParameterKey, JsonElement>
): JsonElement {
    if (segmentSchema !is JsonObject) return segmentSchema

    val childProperties = segmentSchema["properties"] as? JsonObject
    if (childProperties == null) {
        val key = ParameterKey(nodeId, path)
        val value = defaults[key] ?: return segmentSchema
        return JsonObject(segmentSchema.toMutableMap().apply { put("default", value) })
    }

    val updatedChildren = buildJsonObject {
        for ((name, childSchema) in childProperties) {
            put(name, updateSchemaSegment(childSchema, nodeId, path + name, defaults))
        }
    }
    return JsonObject(segmentSchema.toMutableMap().apply { put("properties", updatedChildren) })
}
