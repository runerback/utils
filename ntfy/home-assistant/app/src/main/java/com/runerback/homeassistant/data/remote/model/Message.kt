package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long? = null,
    val sender: String? = null,
    val body: String,
    @SerialName("sent_at")
    val sentAt: String,
    @SerialName("is_mine")
    val isMine: Boolean = false,
)
