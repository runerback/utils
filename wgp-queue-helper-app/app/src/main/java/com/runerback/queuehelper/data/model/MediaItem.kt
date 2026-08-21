package com.runerback.queuehelper.data.model

import kotlinx.serialization.Serializable

/**
 * Persisted metadata for one entry in the media library.
 *
 * [id] is the SHA-256 hash of the source URI string, so the same URI always
 * resolves to the same media entry. [sourceUri] stores the original URI for
 * display and re-resolution.
 */
@Serializable
data class MediaItem(
    val id: String,
    val sourceUri: String?,
    val originalName: String?,
    val mimeType: String?,
    val addedAt: Long
)
