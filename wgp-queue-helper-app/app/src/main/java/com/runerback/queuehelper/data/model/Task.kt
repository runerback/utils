package com.runerback.queuehelper.data.model

import kotlinx.serialization.json.JsonObject

/**
 * A persisted task belonging to a preset.
 *
 * [id] is auto-incremented starting from 1 within the task repository.
 * [presetId] identifies the parent preset.
 * [payload] stores the merged preset data + pack-time overrides and pack_settings.
 */
data class Task(
    val id: Int,
    val presetId: Int,
    val createdAt: Long,
    val payload: JsonObject
)
