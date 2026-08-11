package com.runerback.comfyuiapi.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

typealias Workflow = Map<String, WorkflowNode>

@Serializable
data class WorkflowNode(
    val inputs: JsonObject,
    val class_type: String,
    val _meta: NodeMeta? = null
)

@Serializable
data class NodeMeta(
    val title: String? = null
)

@Serializable
data class PromptRequest(
    val prompt: Workflow,
    val client_id: String,
    val prompt_id: String
)

@Serializable
data class WsMessage(
    val type: String,
    val data: JsonObject? = null
)

@Serializable
data class ImageRef(
    val filename: String,
    val subfolder: String,
    val type: String
)
