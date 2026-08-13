package com.runerback.comfyuiapi.data.datasource

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import com.runerback.comfyuiapi.ui.components.LogBuffer
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectInfoCacheDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    fun get(serverUrl: String, classType: String): JsonObject? {
        val file = cacheFile(serverUrl, classType)
        if (!file.exists()) return null
        return try {
            json.parseToJsonElement(file.readText()) as? JsonObject
        } catch (e: Exception) {
            LogBuffer.add("objectInfoCache.get failed: ${e.message}")
            null
        }
    }

    fun put(serverUrl: String, classType: String, data: JsonObject): Result<Unit> {
        return try {
            val file = cacheFile(serverUrl, classType)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(JsonObject.serializer(), data))
            Result.success(Unit)
        } catch (e: Exception) {
            LogBuffer.add("objectInfoCache.put failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun clear(serverUrl: String? = null, classType: String? = null): Result<Unit> {
        return try {
            when {
                serverUrl == null -> cacheRootDir().deleteRecursively()
                classType == null -> cacheDir(serverUrl).deleteRecursively()
                else -> cacheFile(serverUrl, classType).delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogBuffer.add("objectInfoCache.clear failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun cacheRootDir(): File = File(context.filesDir, "object_info_cache")

    private fun cacheDir(serverUrl: String): File =
        File(cacheRootDir(), serverUrl.toSafeName()).apply { mkdirs() }

    private fun cacheFile(serverUrl: String, classType: String): File {
        val safeClass = classType.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json"
        return File(cacheDir(serverUrl), safeClass)
    }

    private fun String.toSafeName(): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
