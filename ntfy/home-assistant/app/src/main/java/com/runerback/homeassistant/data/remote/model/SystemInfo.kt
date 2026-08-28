package com.runerback.homeassistant.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemInfo(
    @SerialName("cpu_temp")
    val cpuTemp: Double? = null,
    val memory: MemoryInfo? = null,
) {
    @Serializable
    data class MemoryInfo(
        @SerialName("total_kb")
        val totalKb: Long? = null,
        @SerialName("used_kb")
        val usedKb: Long? = null,
        val percent: Double? = null,
    )
}
