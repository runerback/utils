package com.runerback.queuehelper.data.model

import kotlinx.serialization.json.JsonObject

/**
 * A persisted preset definition.
 *
 * [id] is auto-incremented starting from 1.
 * [name] is the user-facing label.
 * [modelType] is the real template key (e.g. "minimax_h3_ref2va_pruned").
 * [payload] stores the merged template + user edits, including the defaults used by Pack.
 */
data class Preset(
    val id: Int,
    val name: String,
    val modelType: String,
    val createdAt: Long,
    val payload: JsonObject
)
