package com.runerback.queuehelper.data.model

import kotlinx.serialization.json.JsonObject

/**
 * A persisted queue job belonging to a preset.
 *
 * [id] is auto-incremented starting from 1 within the job repository.
 * [presetId] identifies the parent preset from Queue Helper.
 * [payload] stores the merged preset data + pack-time overrides and pack_settings.
 */
data class QueueJob(
    val id: Int,
    val presetId: Int,
    val createdAt: Long,
    val payload: JsonObject
)
