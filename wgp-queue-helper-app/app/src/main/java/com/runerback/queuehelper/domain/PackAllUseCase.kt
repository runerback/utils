package com.runerback.queuehelper.domain

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.runerback.queuehelper.data.local.MediaRepository
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.model.MediaRef
import com.runerback.queuehelper.data.model.Task
import com.runerback.queuehelper.ui.components.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackAllUseCase(
    private val context: Context,
    private val taskRepository: TaskRepository,
    private val mediaRepository: MediaRepository,
    private val presetId: Int?
) {
    private val json = Json { prettyPrint = true }

    suspend operator fun invoke(): String = withContext(Dispatchers.IO) {
        runCatching {
            val tasks = if (presetId != null) {
                taskRepository.loadTasks(presetId)
            } else {
                taskRepository.loadAllTasks()
            }.sortedBy { it.createdAt }
            if (tasks.isEmpty()) throw IllegalStateException("No tasks to pack")

            val (outputStream, savedPath) = openDownloadsOutputStream("queue.zip")
            val taskObjects = mutableListOf<JsonObject>()

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                val allRefsById = mutableMapOf<String, MediaRef>()
                val taskImageRefs = mutableMapOf<Int, List<MediaRef>>()

                tasks.forEach { task ->
                    val refs = resolveImageRefs(task)
                    refs.forEach { ref -> allRefsById[ref.id] = ref }
                    taskImageRefs[task.id] = refs
                }

                allRefsById.values.forEach { ref ->
                    zip.addFile(ref.fileName, ref.uri)
                }

                tasks.forEach { task ->
                    val packSettings = task.payload["pack_settings"]?.jsonObject
                    val imageNames = taskImageRefs[task.id]?.map { it.fileName } ?: emptyList()
                    val audioName = addAudioToZip(zip, task, packSettings)

                    val params = buildParams(task, imageNames, audioName)
                    taskObjects.add(
                        buildJsonObject {
                            put("id", JsonPrimitive(task.id))
                            put("params", params)
                        }
                    )
                }

                val queueJson = json.encodeToString(
                    buildJsonArray {
                        taskObjects.forEach { add(it) }
                    }
                )
                zip.putNextEntry(ZipEntry("queue.json"))
                zip.write(queueJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            "Saved to $savedPath"
        }.getOrElse {
            LogBuffer.add("PackAllUseCase: ${it.stackTraceToString()}")
            "Pack failed: ${it.message}"
        }
    }

    private suspend fun resolveImageRefs(task: Task): List<MediaRef> {
        val packSettings = task.payload["pack_settings"]?.jsonObject
        val mediaIds = packSettings?.get("image_media_ids")?.jsonArray?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull
        }
        if (mediaIds != null) {
            return mediaRepository.resolveIds(mediaIds)
        }
        val oldUris = packSettings?.get("image_uris")?.jsonArray?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.let { uriString ->
                runCatching { Uri.parse(uriString) }.getOrNull()
            }
        } ?: emptyList()
        return oldUris.mapNotNull { uri -> mediaRepository.import(uri).getOrNull() }
    }

    private suspend fun addAudioToZip(
        zip: ZipOutputStream,
        task: Task,
        packSettings: JsonObject?
    ): String? {
        val uriString = packSettings?.get("audio_uri")?.jsonPrimitive?.contentOrNull
        val uri = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        val trimStart = packSettings?.get("trim_start")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
        val trimEndRaw = packSettings?.get("trim_end")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
        val trimEnd = if (trimEndRaw > trimStart) trimEndRaw else Float.MAX_VALUE
        val name = "task${task.id}_audio_guide_0.wav"
        val tempAudio = File.createTempFile("audio_${task.id}_", ".wav", context.cacheDir)
        return try {
            AudioTrimmer.trimToWav(context, uri, tempAudio, trimStart, trimEnd).getOrThrow()
            zip.addFile(name, tempAudio)
            name
        } finally {
            tempAudio.delete()
        }
    }

    private fun buildParams(
        task: Task,
        imageNames: List<String>,
        audioName: String?
    ): JsonObject {
        val baseParams = task.payload["params"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        baseParams["image_refs"] = buildJsonArray {
            imageNames.forEach { add(JsonPrimitive(it)) }
        }
        baseParams["audio_guide"] = audioName?.let { JsonPrimitive(it) } ?: JsonNull

        return JsonObject(baseParams)
    }

    private fun openDownloadsOutputStream(fileName: String): Pair<OutputStream, String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create download entry")
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Failed to open download output stream")
            outputStream to "Download/$fileName"
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file) to file.absolutePath
        }
    }

    private fun ZipOutputStream.addFile(entryName: String, uri: Uri) {
        putNextEntry(ZipEntry(entryName))
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedInputStream(input).copyTo(this)
        }
        closeEntry()
    }

    private fun ZipOutputStream.addFile(entryName: String, file: File) {
        putNextEntry(ZipEntry(entryName))
        BufferedInputStream(file.inputStream()).use { input ->
            input.copyTo(this)
        }
        closeEntry()
    }
}
