package com.runerback.tagem.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object ImageStore {

    data class ImageItem(
        val uri: Uri,
        val displayName: String,
        val dateAddedMillis: Long,
        val mimeType: String = "",
    ) {
        val isGif: Boolean
            get() = mimeType.equals("image/gif", ignoreCase = true)
    }

    private val thumbnailCache = LruCache<String, Bitmap>(120)
    private val cacheMutex = Mutex()

    fun listImages(context: Context): List<ImageItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return buildList {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex)
                    val dateMillis = cursor.getLong(dateIndex) * 1000L
                    val mimeType = cursor.getString(mimeIndex) ?: ""
                    add(
                        ImageItem(
                            uri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id.toString(),
                            ),
                            displayName = name,
                            dateAddedMillis = dateMillis,
                            mimeType = mimeType,
                        ),
                    )
                }
            }
        }
    }

    suspend fun loadThumbnail(context: Context, uri: Uri, size: Int = 256): Bitmap? {
        val key = "${uri}_$size"
        cacheMutex.withLock {
            thumbnailCache.get(key)?.let { return it }
        }

        val bitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } catch (_: Exception) {
                null
            }
        }

        bitmap?.let {
            cacheMutex.withLock {
                thumbnailCache.put(key, it)
            }
        }
        return bitmap
    }
}
