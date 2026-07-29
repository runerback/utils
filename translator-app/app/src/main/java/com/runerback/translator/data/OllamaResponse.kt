package com.runerback.translator.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaResponseMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OllamaResponse(
    val model: String,
    @SerialName("created_at") val createdAt: String? = null,
    val message: OllamaResponseMessage,
    val done: Boolean,
    @SerialName("done_reason") val doneReason: String? = null,
)
