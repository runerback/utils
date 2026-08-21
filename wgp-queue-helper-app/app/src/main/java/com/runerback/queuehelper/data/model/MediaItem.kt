package com.runerback.queuehelper.data.model

import kotlinx.serialization.Serializable

/**
 * Persisted metadata for one entry in the media library.
 *
 * [id] is the SHA-256 hash of the file content.
 */
@Serializable
data class MediaItem(
    val id: String,
    val originalName: String?,
    val mimeType: String?,
    val addedAt: Long
)
