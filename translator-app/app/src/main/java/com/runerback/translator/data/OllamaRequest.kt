package com.runerback.translator.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OllamaOptions(
    val temperature: Double,
)

@Serializable
data class OllamaRequest(
    val model: String,
    val think: Boolean,
    val stream: Boolean,
    val messages: List<OllamaMessage>,
    val options: OllamaOptions,
)
