package com.runerback.queuehelper.data.local

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.runerback.queuehelper.data.model.MediaItem
import com.runerback.queuehelper.data.model.MediaRef
import com.runerback.queuehelper.ui.components.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class MediaRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val mediaDir: File
        get() = File(context.filesDir, "media").apply { mkdirs() }

    private val indexFile: File
        get() = File(mediaDir, "media_index.json")

    /**
     * Imports an image into the media library.
     *
     * The file is content-addressed by SHA-256. Importing the same bytes twice
     * returns the same [MediaRef] without writing a second file.
     */
    suspend fun import(uri: Uri): Result<MediaRef> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Failed to open input stream for $uri")

            val id = sha256(bytes)
            val extension = extensionForUri(uri)
            val destFile = mediaFile(id, extension)

            if (!destFile.exists()) {
                destFile.writeBytes(bytes)
                updateIndex { current ->
                    current.filter { it.id != id } + MediaItem(
                        id = id,
                        originalName = uri.lastPathSegment,
                        mimeType = context.contentResolver.getType(uri),
                        addedAt = System.currentTimeMillis()
                    )
                }
            }

            MediaRef(
                id = id,
                uri = Uri.fromFile(destFile),
                fileName = fileNameFor(id)
            )
        }.onFailure {
            LogBuffer.add("MediaRepository.import($uri): ${it.stackTraceToString()}")
        }
    }

    /**
     * Returns a [MediaRef] for an existing media ID, or null if not found.
     */
    suspend fun get(id: String): MediaRef? = withContext(Dispatchers.IO) {
        val item = readIndex().find { it.id == id } ?: return@withContext null
        val file = mediaFile(item.id, extensionForItem(item))
        if (!file.exists()) return@withContext null
        MediaRef(
            id = item.id,
            uri = Uri.fromFile(file),
            fileName = fileNameFor(item.id)
        )
    }

    /**
     * Resolves a list of media IDs to refs, skipping any that no longer exist.
     */
    suspend fun resolveIds(ids: List<String>): List<MediaRef> = withContext(Dispatchers.IO) {
        val index = readIndex().associateBy { it.id }
        ids.mapNotNull { id ->
            val item = index[id] ?: return@mapNotNull null
            val file = mediaFile(id, extensionForItem(item))
            if (!file.exists()) return@mapNotNull null
            MediaRef(
                id = id,
                uri = Uri.fromFile(file),
                fileName = fileNameFor(id)
            )
        }
    }

    fun fileNameFor(mediaId: String): String = "image_${mediaId}.png"

    private fun mediaFile(id: String, extension: String): File {
        val ext = extension.removePrefix(".").ifBlank { "bin" }
        return File(mediaDir, "${id}.${ext}")
    }

    private fun extensionForUri(uri: Uri): String {
        uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }?.let { return it }
        context.contentResolver.getType(uri)?.let { mime ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
        }
        return "bin"
    }

    private fun extensionForItem(item: MediaItem): String {
        item.originalName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }?.let { return it }
        item.mimeType?.let { mime ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
        }
        return "bin"
    }

    private fun readIndex(): List<MediaItem> {
        return runCatching {
            if (!indexFile.exists()) return@runCatching emptyList<MediaItem>()
            json.decodeFromString<List<MediaItem>>(indexFile.readText())
        }.getOrElse {
            LogBuffer.add("MediaRepository.readIndex: ${it.stackTraceToString()}")
            emptyList()
        }
    }

    private fun updateIndex(transform: (List<MediaItem>) -> List<MediaItem>) {
        val updated = transform(readIndex())
        indexFile.writeText(json.encodeToString(updated))
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
