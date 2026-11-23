package com.runerback.videohelper.data.api

import android.util.Log
import com.runerback.videohelper.data.settings.ServerSettings
import com.runerback.videohelper.data.settings.SettingsRepository
import com.runerback.videohelper.util.LogBuffer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VideoHelperApi"

class ApiException(val statusCode: Int, message: String) : Exception(message)

@Singleton
class VideoHelperApi @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(Logging) {
            level = LogLevel.HEADERS
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                    LogBuffer.append(message)
                }
            }
        }
    }

    private suspend fun settings(): ServerSettings {
        val s = settingsRepository.settings.first()
        check(s.isConfigured) { "Server URL is not configured" }
        return s
    }

    /** Absolute URL for a server-relative path, with the auth token as query param
     * (for media players and DownloadManager, which can't easily set headers). */
    suspend fun mediaUrl(path: String): String {
        val s = settings()
        val separator = if (path.contains('?')) "&" else "?"
        val tokenParam = if (s.token.isBlank()) "" else "${separator}token=${s.token}"
        return "${s.baseUrl}$path$tokenParam"
    }

    suspend fun listProjects(): List<ProjectListItemDto> {
        val s = settings()
        return client.get("${s.baseUrl}/api/projects") {
            header("X-Token", s.token)
        }.body()
    }

    suspend fun uploadProject(fileName: String, bytes: ByteArray, mimeType: String): ProjectCreateResponseDto {
        val s = settings()
        return client.post("${s.baseUrl}/api/projects") {
            header("X-Token", s.token)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }.body()
    }

    suspend fun getProject(projectId: String): ProjectStateResponseDto {
        val s = settings()
        return client.get("${s.baseUrl}/api/projects/$projectId") {
            header("X-Token", s.token)
        }.body()
    }

    suspend fun updateState(projectId: String, state: EditStateDto): ProjectStateResponseDto {
        val s = settings()
        return client.put("${s.baseUrl}/api/projects/$projectId/state") {
            header("X-Token", s.token)
            contentType(ContentType.Application.Json)
            setBody(StateUpdateRequestDto(state))
        }.body()
    }

    suspend fun estimateGif(projectId: String): ExportEstimateResponseDto {
        val s = settings()
        return client.post("${s.baseUrl}/api/projects/$projectId/export/gif-estimate") {
            header("X-Token", s.token)
        }.body()
    }

    suspend fun createPreviewJob(projectId: String): JobCreateResponseDto =
        createJob(projectId, "jobs/preview")

    suspend fun createExportJob(projectId: String): JobCreateResponseDto =
        createJob(projectId, "jobs/export")

    suspend fun createExportGifJob(projectId: String): JobCreateResponseDto =
        createJob(projectId, "jobs/export/gif")

    private suspend fun createJob(projectId: String, kindPath: String): JobCreateResponseDto {
        val s = settings()
        return client.post("${s.baseUrl}/api/projects/$projectId/$kindPath") {
            header("X-Token", s.token)
        }.body()
    }

    suspend fun getJob(jobId: String): JobResponseDto {
        val s = settings()
        return client.get("${s.baseUrl}/api/jobs/$jobId") {
            header("X-Token", s.token)
        }.body()
    }

    /** Server-relative download path for the single-file export. */
    fun exportDownloadPath(projectId: String): String = "/api/projects/$projectId/export/download"
}
