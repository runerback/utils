package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerSettings(
    @SerialName("messages.server_url")
    val serverUrl: String = "",
    @SerialName("messages.token")
    val token: String = "",
)
