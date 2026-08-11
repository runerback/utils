package com.runerback.comfyuiapi.data.datasource

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.runerback.comfyuiapi.ui.components.LogBuffer
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
