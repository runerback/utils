package com.runerback.files.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FileMetadata(
    val size: Long? = null,
    val lastModified: Long? = null,
    val mimeType: String? = null
)
