package com.runerback.screenrecorder.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingStore {
    private const val RELATIVE_PATH = "Movies/Screen Recorder"

    data class PendingRecording(
        val uri: Uri,
        val fileDescriptor: ParcelFileDescriptor,
        val displayName: String,
    )

    data class RecordingItem(
        val uri: Uri,
        val displayName: String,
        val dateAddedMillis: Long,
        val sizeBytes: Long,
        val durationMillis: Long?,
    )

    @Throws(IOException::class)
    fun createPendingRecording(context: Context): PendingRecording {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val displayName = "ScreenRecorder-$timestamp.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create the recording output.")
        val fileDescriptor = resolver.openFileDescriptor(uri, "w")
            ?: throw IOException("Unable to open the recording output.")

        return PendingRecording(
            uri = uri,
            fileDescriptor = fileDescriptor,
            displayName = displayName,
        )
    }

    fun finalizeRecording(context: Context, uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    fun deleteRecording(context: Context, uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }

    fun listRecordings(context: Context): List<RecordingItem> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
        )

        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$RELATIVE_PATH%")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        return buildList {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex)
                    val dateMillis = cursor.getLong(dateIndex) * 1000L
                    val size = cursor.getLong(sizeIndex)
                    val duration = if (cursor.isNull(durationIndex)) null else cursor.getLong(durationIndex)
                    add(
                        RecordingItem(
                            uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()),
                            displayName = name,
                            dateAddedMillis = dateMillis,
                            sizeBytes = size,
                            durationMillis = duration,
                        ),
                    )
                }
            }
        }
    }
}
