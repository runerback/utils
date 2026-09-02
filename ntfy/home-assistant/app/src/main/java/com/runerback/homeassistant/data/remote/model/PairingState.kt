package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PairingResult(
    @SerialName("device_id") val deviceId: String,
    val name: String,
    val status: String,
    @SerialName("qr_payload") val qrPayload: String,
)

@Serializable
data class DeviceEvent(
    val type: String,
    @SerialName("device_id") val deviceId: String? = null,
)
