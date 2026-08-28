package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageEvent(
    val type: String,
    val topic: String,
    val sender: String? = null,
    val body: String? = null,
    @SerialName("sent_at")
    val sentAt: String? = null,
)
