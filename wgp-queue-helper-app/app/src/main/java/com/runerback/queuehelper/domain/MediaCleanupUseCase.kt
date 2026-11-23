package com.runerback.queuehelper.domain

import com.runerback.queuehelper.data.local.MediaPruneResult
import com.runerback.queuehelper.data.local.MediaRepository
import com.runerback.queuehelper.data.local.MediaStoreStats
import com.runerback.queuehelper.data.local.PresetRepository
import com.runerback.queuehelper.data.local.TaskRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Computes which media files are still referenced by tasks/presets and prunes
 * the rest. References live in `pack_settings` as `image_media_ids` and
 * `audio_media_id`; legacy payloads reference content URIs via `image_uris` /
 * `audio_uri`, which resolve to media IDs by hashing the URI string.
 */
class MediaCleanupUseCase(
    private val taskRepository: TaskRepository,
    private val presetRepository: PresetRepository,
    private val mediaRepository: MediaRepository
) {

    suspend fun stats(): MediaStoreStats = mediaRepository.stats()

    suspend fun prune(): MediaPruneResult {
        val result = mediaRepository.pruneUnreferenced(referencedMediaIds())
        mediaRepository.clearTempAudio()
        return result
    }

    private suspend fun referencedMediaIds(): Set<String> {
        val ids = mutableSetOf<String>()
        val payloads = taskRepository.loadAllTasks().map { it.payload } +
            presetRepository.loadPresets().map { it.payload }
        payloads.forEach { payload ->
            val settings = payload["pack_settings"]?.jsonObject ?: return@forEach
            (settings["image_media_ids"] as? JsonArray)?.forEach { element ->
                element.jsonPrimitive.contentOrNull?.let { ids.add(it) }
            }
            settings["audio_media_id"]?.jsonPrimitive?.contentOrNull?.let { ids.add(it) }
            (settings["image_uris"] as? JsonArray)?.forEach { element ->
                element.jsonPrimitive.contentOrNull?.let { ids.add(mediaRepository.idForSourceUri(it)) }
            }
            settings["audio_uri"]?.jsonPrimitive?.contentOrNull?.let {
                ids.add(mediaRepository.idForSourceUri(it))
            }
        }
        return ids
    }
}
