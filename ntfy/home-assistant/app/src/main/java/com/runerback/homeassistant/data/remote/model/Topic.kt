package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Topic(
    val id: Long,
    val name: String,
    @SerialName("latest_body")
    val latestBody: String? = null,
    @SerialName("latest_sent_at")
    val latestSentAt: String? = null,
    val status: String? = null,
)
