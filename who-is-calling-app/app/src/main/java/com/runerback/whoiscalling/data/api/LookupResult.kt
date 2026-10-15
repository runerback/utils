package com.runerback.whoiscalling.data.api

import kotlinx.serialization.Serializable

@Serializable
data class LookupResult(
    val isSpam: Boolean = false,
    val sourceArea: String? = null,
    val sourceName: String? = null,
)
