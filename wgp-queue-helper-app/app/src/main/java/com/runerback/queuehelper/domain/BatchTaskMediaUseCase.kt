package com.runerback.queuehelper.domain

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.runerback.queuehelper.data.local.MediaRepository
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.model.MediaRef
import com.runerback.queuehelper.data.model.MiniMaxH3Ref2VaPrompt
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.SubjectDefaults
import com.runerback.queuehelper.data.model.Task
import com.runerback.queuehelper.data.model.formatSubjectDefinitions
import com.runerback.queuehelper.data.model.parseSubjectDefinitions
import com.runerback.queuehelper.data.template.TemplateLoader
import com.runerback.queuehelper.data.template.VideoLengthRule
import com.runerback.queuehelper.ui.components.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val MAX_TASK_IMAGES = 6

data class BatchMediaResult(
    val updatedCount: Int,
    val skippedCount: Int,
    val message: String
)

class BatchTaskMediaUseCase(
    private val context: Context,
    private val taskRepository: TaskRepository,
    private val mediaRepository: MediaRepository,
    private val templateLoader: TemplateLoader
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun appendImages(taskIds: Set<Int>, uris: List<Uri>): BatchMediaResult =
        withContext(Dispatchers.IO) {
            val imported = uris.mapNotNull { uri ->
                mediaRepository.import(uri).getOrNull()
            }.distinctBy { it.id }

            if (taskIds.isEmpty() || imported.isEmpty()) {
                return@withContext BatchMediaResult(0, taskIds.size, "No images added")
            }

            var updatedCount = 0
            var skippedCount = 0
            taskIds.forEach { taskId ->
                val task = taskRepository.loadTask(taskId)
                if (task == null) {
                    skippedCount++
                    return@forEach
                }

                val existingIds = existingImageIds(task)
                val mergedIds = TaskMediaPayloadUpdater.mergeImageIds(
                    existingIds,
                    imported.map { it.id }
                )
                if (mergedIds == existingIds) {
                    skippedCount++
                    return@forEach
                }

                val refs = mediaRepository.resolveIds(mergedIds)
                val payload = TaskMediaPayloadUpdater.withImages(
                    payload = task.payload,
                    mediaIds = refs.map { it.id },
                    fileNameFor = mediaRepository::fileNameFor
                )
                taskRepository.saveTask(task.copy(payload = payload))
                updatedCount++
            }

            val message = buildString {
                append("Added images to $updatedCount task")
                if (updatedCount != 1) append('s')
                if (skippedCount > 0) {
                    append(" ($skippedCount already full or unavailable)")
                }
            }
            BatchMediaResult(updatedCount, skippedCount, message)
        }

    suspend fun replaceAudio(taskIds: Set<Int>, uri: Uri): BatchMediaResult =
        withContext(Dispatchers.IO) {
            val audioRef = mediaRepository.import(uri).getOrNull()
                ?: return@withContext BatchMediaResult(0, taskIds.size, "Failed to import audio")
            val durationSeconds = readAudioDuration(audioRef.uri)

            var updatedCount = 0
            var skippedCount = 0
            taskIds.forEach { taskId ->
                val task = taskRepository.loadTask(taskId)
                if (task == null) {
                    skippedCount++
                    return@forEach
                }

                val maxDuration = maxAudioDurationSeconds(task)
                val trimEnd = durationSeconds.coerceAtMost(maxDuration)
                val effectiveEnd = if (trimEnd > 0f) trimEnd else Float.MAX_VALUE
                val audioFileName = mediaRepository.audioFileNameFor(audioRef.id, 0f, effectiveEnd)
                val payload = TaskMediaPayloadUpdater.withAudio(
                    payload = task.payload,
                    mediaId = audioRef.id,
                    trimStart = 0f,
                    trimEnd = trimEnd,
                    audioFileName = audioFileName,
                    videoLength = computedVideoLength(task, trimEnd)
                )
                taskRepository.saveTask(task.copy(payload = payload))
                updatedCount++
            }

            val message = buildString {
                append("Updated audio on $updatedCount task")
                if (updatedCount != 1) append('s')
                if (skippedCount > 0) append(" ($skippedCount unavailable)")
            }
            BatchMediaResult(updatedCount, skippedCount, message)
        }

    private suspend fun existingImageIds(task: Task): List<String> {
        val settings = task.payload["pack_settings"]?.jsonObject ?: return emptyList()
        settings["image_media_ids"]?.jsonArray?.let { ids ->
            return ids.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
        val legacyUris = settings["image_uris"]?.jsonArray?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.let { uriString ->
                runCatching { Uri.parse(uriString) }.getOrNull()
            }
        } ?: emptyList()
        return legacyUris.mapNotNull { mediaRepository.import(it).getOrNull()?.id }
    }

    private fun maxAudioDurationSeconds(task: Task): Float {
        val modelType = task.payload["params"]?.jsonObject
            ?.get("model_type")?.jsonPrimitive?.contentOrNull
            ?: return Float.MAX_VALUE
        return runCatching {
            templateLoader.config(modelType).maxAudioDurationSeconds ?: Float.MAX_VALUE
        }.getOrElse {
            LogBuffer.add("BatchTaskMediaUseCase.maxAudioDurationSeconds($modelType): ${it.stackTraceToString()}")
            Float.MAX_VALUE
        }
    }

    private fun computedVideoLength(task: Task, trimEnd: Float): Int {
        val modelType = task.payload["params"]?.jsonObject
            ?.get("model_type")?.jsonPrimitive?.contentOrNull
        val rule = modelType?.let {
            runCatching { templateLoader.config(it).videoLengthRule }.getOrNull()
        } ?: VideoLengthRule.AudioDurationMultiplier()
        if (rule !is VideoLengthRule.AudioDurationMultiplier) return 0
        return (trimEnd.coerceAtLeast(0f) * rule.multiplier).toInt().coerceAtMost(rule.max)
    }

    private fun readAudioDuration(uri: Uri): Float {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            durationMs / 1000f
        } catch (e: Exception) {
            LogBuffer.add("BatchTaskMediaUseCase.readAudioDuration($uri): ${e.stackTraceToString()}")
            0f
        } finally {
            retriever.release()
        }
    }
}

object TaskMediaPayloadUpdater {
    private val json = Json { ignoreUnknownKeys = true }

    fun mergeImageIds(existingIds: List<String>, addedIds: List<String>): List<String> =
        (existingIds + addedIds).distinct().take(MAX_TASK_IMAGES)

    fun withImages(
        payload: JsonObject,
        mediaIds: List<String>,
        fileNameFor: (String) -> String
    ): JsonObject {
        val params = payload["params"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        params["image_refs"] = buildJsonArray {
            mediaIds.forEach { add(JsonPrimitive(fileNameFor(it))) }
        }

        val settings = payload["pack_settings"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        settings["image_media_ids"] = buildJsonArray {
            mediaIds.forEach { add(JsonPrimitive(it)) }
        }

        return JsonObject(payload.toMutableMap().apply {
            put("params", JsonObject(params))
            put("pack_settings", JsonObject(settings))
        })
    }

    fun withAudio(
        payload: JsonObject,
        mediaId: String,
        trimStart: Float,
        trimEnd: Float,
        audioFileName: String,
        videoLength: Int
    ): JsonObject {
        val params = payload["params"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        val prompt = MiniMaxH3Ref2VaPrompt.parse(
            params["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
        )
        val (subjects, _, otherLines) = parseSubjectDefinitions(prompt.subjectDefinitions)
        val audioDefault = audioDefaultFor(payload, prompt)
        val updatedPrompt = prompt.copy(
            subjectDefinitions = formatSubjectDefinitions(subjects, audioDefault, otherLines)
        )

        params["prompt"] = JsonPrimitive(updatedPrompt.toPromptString())
        params["video_length"] = JsonPrimitive(videoLength)
        params["audio_guide"] = JsonPrimitive(audioFileName)

        val settings = payload["pack_settings"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        settings["audio_media_id"] = JsonPrimitive(mediaId)
        settings["trim_start"] = JsonPrimitive(trimStart)
        settings["trim_end"] = JsonPrimitive(trimEnd)
        settings["video_length_input"] = JsonPrimitive(videoLength.toString())

        return JsonObject(payload.toMutableMap().apply {
            put("params", JsonObject(params))
            put("pack_settings", JsonObject(settings))
        })
    }

    private fun audioDefaultFor(
        payload: JsonObject,
        prompt: MiniMaxH3Ref2VaPrompt
    ): String {
        payload["subject_defaults"]?.let { defaultsElement ->
            runCatching {
                json.decodeFromJsonElement(SubjectDefaults.serializer(), defaultsElement).audio
            }.getOrNull()?.let { return it }
        }
        return parseSubjectDefinitions(prompt.subjectDefinitions).second
            ?: SubjectDefinition.defaultAudioDefinition()
    }
}
