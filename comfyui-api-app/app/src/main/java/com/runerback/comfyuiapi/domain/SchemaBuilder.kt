package com.runerback.comfyuiapi.domain

import com.runerback.comfyuiapi.data.model.SchemaFieldRole
import com.runerback.comfyuiapi.data.model.SchemaFieldSelection
import com.runerback.comfyuiapi.data.model.SchemaFieldType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun buildSchema(selections: List<SchemaFieldSelection>): JsonObject {
    val selected = selections.filter { it.selected }
    val grouped = selected.groupBy { it.nodeId }

    return buildJsonObject {
        put("\$schema", "https://json-schema.org/draft/2020-12/schema")
        put("type", "object")
        putJsonObject("properties") {
            for ((nodeId, nodeSelections) in grouped) {
                putJsonObject(nodeId) {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("inputs") {
                            put("type", "object")
                            putJsonObject("properties") {
                                    for (selection in nodeSelections.sortedBy { it.order }) {
                                    putJsonObject(selection.fieldName) {
                                        val effectiveType = if (selection.precision > 0) {
                                            SchemaFieldType.Number.value
                                        } else {
                                            selection.type.value
                                        }
                                        put("type", effectiveType)
                                        if (selection.role != SchemaFieldRole.None) {
                                            put("role", selection.role.value!!)
                                        }
                                        if (selection.role == SchemaFieldRole.Upload) {
                                            put("uploadType", selection.uploadType)
                                            put("mimeType", selection.mimeType)
                                        }
                                        if (selection.role == SchemaFieldRole.Option) {
                                            put("optionKind", selection.optionKind)
                                        }
                                        selection.min?.let { put("minimum", it) }
                                        selection.max?.let { put("maximum", it) }
                                        if (selection.type == SchemaFieldType.String) {
                                            put("multiline", selection.multiline)
                                        }
                                        if (selection.type == SchemaFieldType.Integer || selection.type == SchemaFieldType.Number) {
                                            put("precision", selection.precision)
                                        }
                                        put("default", selection.currentValue)
                                        put("order", selection.order)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
