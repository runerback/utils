package com.runerback.ntfyclient.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
data class NtfyMessage(
    val id: String,
    val time: Long,
    val expires: Long? = null,
    val event: String,
    val topic: String,
    val sequenceId: String? = null,
    val message: String? = null,
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val priority: Int? = null,
    val click: String? = null,
    val actions: JsonArray? = null,
    val attachment: NtfyAttachment? = null,
)

@Serializable
data class NtfyAttachment(
    val name: String,
    val url: String,
    val type: String? = null,
    val size: Long? = null,
    val expires: Long? = null,
)
