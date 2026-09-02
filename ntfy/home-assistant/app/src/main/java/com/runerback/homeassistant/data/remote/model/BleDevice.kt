package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)
