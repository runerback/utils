package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class BluetoothStatus(
    val available: Boolean = false,
    val powered: Boolean = false,
    val adapter: String? = null,
)
