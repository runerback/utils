package com.runerback.files.data.datasource

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.runerback.files.ui.components.LogBuffer
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {

    suspend fun loadJsonObject(uri: Uri): Result<JsonObject> {
        LogBuffer.add("fileDataSource.loadJsonObject: $uri")
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader().readText()
                LogBuffer.add("fileDataSource.loadJsonObject: ${text.length} chars")
                Result.success(json.parseToJsonElement(text).jsonObject)
            } ?: Result.failure(IOException("Cannot open $uri"))
        } catch (e: Exception) {
            LogBuffer.add("fileDataSource.loadJsonObject: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveJsonObject(uri: Uri, jsonObject: JsonObject): Result<Unit> {
        LogBuffer.add("fileDataSource.saveJsonObject: $uri")
        return try {
            context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                val text = json.encodeToString(JsonObject.serializer(), jsonObject)
                stream.write(text.toByteArray())
                LogBuffer.add("fileDataSource.saveJsonObject: ${text.length} chars")
                Result.success(Unit)
            } ?: Result.failure(IOException("Cannot open $uri"))
        } catch (e: Exception) {
            LogBuffer.add("fileDataSource.saveJsonObject: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun loadBytes(uri: Uri): Result<Pair<String, ByteArray>> {
        LogBuffer.add("fileDataSource.loadBytes: $uri")
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                val name = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
                LogBuffer.add("fileDataSource.loadBytes: $name, ${bytes.size} bytes")
                Result.success(name to bytes)
            } ?: Result.failure(IOException("Cannot open $uri"))
        } catch (e: Exception) {
            LogBuffer.add("fileDataSource.loadBytes: ${e.message}")
            Result.failure(e)
        }
    }

    fun saveCachedOutput(filename: String, bytes: ByteArray): Result<Uri> {
        LogBuffer.add("fileDataSource.saveCachedOutput: $filename ${bytes.size} bytes")
        return try {
            val dir = File(context.cacheDir, "outputs").apply { mkdirs() }
            val file = File(dir, filename)
            file.writeBytes(bytes)
            LogBuffer.add("fileDataSource.saveCachedOutput: ${file.absolutePath}")
            Result.success(Uri.fromFile(file))
        } catch (e: Exception) {
            LogBuffer.add("fileDataSource.saveCachedOutput: ${e.message}")
            Result.failure(e)
        }
    }

    fun saveToDownloads(filename: String, bitmap: ImageBitmap): Result<Unit> {
        LogBuffer.add("fileDataSource.saveToDownloads: bitmap $filename")
        return try {
            val androidBitmap = bitmap.asAndroidBitmap()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IOException("Failed to create download entry"))
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return Result.failure(IOException("Failed to open download output stream"))
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                LogBuffer.add("fileDataSource.saveToDownloads: saved $uri")
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.apply { mkdirs() }
                    ?: return Result.failure(IOException("Downloads directory not available"))
                val file = File(dir, filename)
                file.outputStream().use { out ->
                    androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                LogBuffer.add("fileDataSource.saveToDownloads: saved ${file.absolutePath}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogBuffer.add("fileDataSource.saveToDownloads: bitmap error ${e.message}")
            Result.failure(e)
        }
    }

    fun saveToDownloads(filename: String, sourceUri: Uri): Result<Unit> {
        LogBuffer.add("fileDataSource.saveToDownloads: uri $filename")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mimeType = context.contentResolver.getType(sourceUri) ?: "*/*"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IOException("Failed to create download entry"))
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output)
                    } ?: throw IOException("Failed to open download output stream")
                } ?: throw IOException("Failed to open source input stream")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                LogBuffer.add("fileDataSource.saveToDownloads: saved $uri")
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.apply { mkdirs() }
                    ?: return Result.failure(IOException("Downloads directory not available"))
                val file = File(dir, filename)
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Failed to open source input stream")
                LogBuffer.add("fileDataSource.saveToDownloads: saved ${file.absolutePath}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogBuffer.add("fileDataSource.saveToDownloads: uri error ${e.message}")
            Result.failure(e)
        }
    }

    fun displayName(uri: Uri): String {
        return queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

private val JsonElement.jsonObject: JsonObject
    get() = this as? JsonObject ?: throw IllegalArgumentException("Expected JSON object")
