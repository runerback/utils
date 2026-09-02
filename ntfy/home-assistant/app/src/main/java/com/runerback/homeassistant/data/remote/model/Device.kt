package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    @SerialName("device_id") val deviceId: String,
    @SerialName("ble_mac") val bleMac: String?,
    val status: String,
    @SerialName("created_at") val createdAt: Double,
    @SerialName("claimed_at") val claimedAt: Double?,
)
