package com.runerback.files.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TabConfig(
    val id: String,
    val name: String,
    val source: FileSource
)
