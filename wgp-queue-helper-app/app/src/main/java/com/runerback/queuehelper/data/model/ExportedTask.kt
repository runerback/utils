package com.runerback.queuehelper.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Serializable representation of a [Task] used for export/import JSON files.
 *
 * On import, [id] and [createdAt] are discarded and fresh values are assigned
 * to avoid collisions with existing presets.
 */
@Serializable
data class ExportedTask(
    val id: Int,
    val name: String,
    val modelType: String,
    val createdAt: Long,
    val payload: JsonObject
)
