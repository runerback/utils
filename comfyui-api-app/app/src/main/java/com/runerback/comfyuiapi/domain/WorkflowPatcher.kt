package com.runerback.comfyuiapi.domain

import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.data.model.Workflow
import com.runerback.comfyuiapi.data.model.WorkflowNode
import com.runerback.comfyuiapi.ui.components.LogBuffer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkflowPatcher @Inject constructor() {

    fun patch(workflow: Workflow, values: Map<ParameterKey, JsonElement>): Workflow {
        LogBuffer.add("workflowPatcher.patch: ${values.size} values")
        values.forEach { LogBuffer.add("  ${it.key.nodeId}/${it.key.path.joinToString("/")} -> ${it.value}") }
        val patchedNodes = workflow.toMutableMap()

        val grouped = values.entries.groupBy { it.key.nodeId }

        for ((nodeId, entries) in grouped) {
            val originalNode = workflow[nodeId] ?: continue
            val patches = entries.map {
                val path = it.key.path
                val relativePath = if (path.firstOrNull() == "inputs") path.drop(1) else path
                relativePath to it.value
            }
            val newInputs = patchJsonObject(originalNode.inputs, patches)
            patchedNodes[nodeId] = originalNode.copy(inputs = newInputs)
        }

        return patchedNodes
    }

    private fun patchJsonObject(
        root: JsonObject,
        patches: List<Pair<List<String>, JsonElement>>
    ): JsonObject {
        val rootMap = root.toMutableMap()

        for ((path, value) in patches) {
            if (path.isEmpty()) continue
            setAtPath(rootMap, path, value)
        }

        return JsonObject(rootMap)
    }

    private fun setAtPath(map: MutableMap<String, JsonElement>, path: List<String>, value: JsonElement) {
        if (path.size == 1) {
            map[path.first()] = value
            return
        }

        val head = path.first()
        val tail = path.drop(1)
        val current = map[head]
        val childMap = if (current is JsonObject) {
            current.toMutableMap()
        } else {
            mutableMapOf()
        }

        setAtPath(childMap, tail, value)
        map[head] = JsonObject(childMap)
    }
}
