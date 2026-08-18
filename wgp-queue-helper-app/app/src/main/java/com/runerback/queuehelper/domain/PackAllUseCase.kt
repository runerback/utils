package com.runerback.queuehelper.domain

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.runerback.queuehelper.data.local.QueueJobRepository
import com.runerback.queuehelper.data.model.QueueJob
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
    private val queueJobRepository: QueueJobRepository,
    private val presetId: Int
) {
    private val json = Json { prettyPrint = true }

    suspend operator fun invoke(): String = withContext(Dispatchers.IO) {
        runCatching {
            val jobs = queueJobRepository.loadJobs(presetId)
            if (jobs.isEmpty()) throw IllegalStateException("No jobs to pack")

            val (outputStream, savedPath) = openDownloadsOutputStream("queue.zip")
            val jobObjects = mutableListOf<JsonObject>()

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
                jobs.forEach { job ->
                    val packSettings = job.payload["pack_settings"]?.jsonObject
                    val imageNames = addImagesToZip(zip, job, packSettings)
                    val audioName = addAudioToZip(zip, job, packSettings)

                    val params = buildParams(job, imageNames, audioName)
                    jobObjects.add(
                        buildJsonObject {
                            put("id", JsonPrimitive(job.id))
                            put("params", params)
                        }
                    )
                }

                val queueJson = json.encodeToString(
                    buildJsonArray {
                        jobObjects.forEach { add(it) }
                    }
                )
                zip.putNextEntry(ZipEntry("queue.json"))
                zip.write(queueJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            "Saved to $savedPath"
        }.getOrElse { "Pack failed: ${it.message}" }
    }

    private fun addImagesToZip(
        zip: ZipOutputStream,
        job: QueueJob,
        packSettings: JsonObject?
    ): List<String> {
        val uris = packSettings?.get("image_uris")?.jsonArray?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.let { uriString ->
                runCatching { Uri.parse(uriString) }.getOrNull()
            }
        } ?: emptyList()

        return uris.mapIndexed { index, uri ->
            val name = "job${job.id}_image_refs_$index.png"
            zip.addFile(name, uri)
            name
        }
    }

    private fun addAudioToZip(
        zip: ZipOutputStream,
        job: QueueJob,
        packSettings: JsonObject?
    ): String? {
        val uriString = packSettings?.get("audio_uri")?.jsonPrimitive?.contentOrNull
        val uri = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        val name = "job${job.id}_audio_guide_0.flac"
        zip.addFile(name, uri)
        return name
    }

    private fun buildParams(
        job: QueueJob,
        imageNames: List<String>,
        audioName: String?
    ): JsonObject {
        val baseParams = job.payload["params"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

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
}
