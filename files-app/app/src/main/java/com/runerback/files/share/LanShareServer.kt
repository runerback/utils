package com.runerback.files.share

import com.runerback.files.data.model.FileNode
import com.runerback.files.data.repository.FileRepository
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class LanShareServer(
    private val repository: FileRepository
) {

    data class SharedFile(
        val name: String,
        val nodeId: String,
        val mimeType: String,
        val size: Long?
    )

    private var engine: ApplicationEngine? = null
    private val _sharedFiles = mutableMapOf<String, SharedFile>()
    private var _baseUrl: String? = null
    private var _token: String = ""

    val baseUrl: String?
        get() = _baseUrl

    val sharedFiles: Map<String, SharedFile>
        get() = _sharedFiles.toMap()

    fun isRunning(): Boolean = engine != null

    suspend fun start(files: List<FileNode>): String? {
        stop()

        val ip = getLanIpAddress() ?: return null
        val port = findFreePort()
        _token = randomToken()

        _sharedFiles.clear()
        files.filter { !it.isDirectory }.forEach { node ->
            val shareId = randomToken(8)
            _sharedFiles[shareId] = SharedFile(
                name = node.name,
                nodeId = node.id,
                mimeType = node.metadata.mimeType ?: "*/*",
                size = node.metadata.size
            )
        }

        if (_sharedFiles.isEmpty()) return null

        _baseUrl = "http://$ip:$port/$_token"

        engine = embeddedServer(CIO, port = port) {
            routing {
                get("/$_token/{shareId}/{fileName?}") {
                    serveFile(call)
                }

                get("/$_token/{shareId}") {
                    serveFile(call)
                }

                get("/$_token") {
                    val html = buildString {
                        appendLine("<!DOCTYPE html>")
                        appendLine("<html>")
                        appendLine("<head><meta charset=\"UTF-8\"><title>Shared files</title></head>")
                        appendLine("<body>")
                        appendLine("<h1>Shared files</h1>")
                        appendLine("<ul>")
                        _sharedFiles.forEach { (shareId, sharedFile) ->
                            val escapedName = htmlEscape(sharedFile.name)
                            val fileUrl = "/$_token/$shareId/${urlEncode(sharedFile.name)}"
                            appendLine("<li><a href=\"$fileUrl\" download=\"$escapedName\" data-filename=\"$escapedName\" title=\"$escapedName\" style=\"display:inline-block;min-width:120px;padding:4px 0;\">$escapedName</a></li>")
                        }
                        appendLine("</ul>")
                        appendLine("</body>")
                        appendLine("</html>")
                    }
                    call.respondText(
                        text = html,
                        contentType = ContentType.Text.Html
                    )
                }
            }
        }.start(wait = false)

        return _baseUrl
    }

    fun stop() {
        engine?.stop(500, 1000)
        engine = null
        _baseUrl = null
        _sharedFiles.clear()
    }

    private suspend fun serveFile(call: io.ktor.server.application.ApplicationCall) {
        val shareId = call.parameters["shareId"] ?: return call.respondText(
            "Missing share id",
            status = HttpStatusCode.BadRequest
        )
        val sharedFile = _sharedFiles[shareId] ?: return call.respondText(
            "Not found",
            status = HttpStatusCode.NotFound
        )

        val inputStreamResult = withContext(Dispatchers.IO) {
            repository.openInputStream(sharedFile.nodeId)
        }

        inputStreamResult.fold(
            onSuccess = { stream ->
                stream.use { input ->
                    call.response.headers.append(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment
                            .withParameter(ContentDisposition.Parameters.FileName, sharedFile.name)
                            .toString()
                    )
                    sharedFile.size?.let { size ->
                        call.response.headers.append(HttpHeaders.ContentLength, size.toString())
                    }
                    val contentType = try {
                        ContentType.parse(sharedFile.mimeType)
                    } catch (e: Exception) {
                        ContentType.Application.OctetStream
                    }
                    call.respondOutputStream(contentType = contentType) {
                        input.copyTo(this)
                    }
                }
            },
            onFailure = { e ->
                call.respondText(
                    "Failed to open file: ${e.message}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        )
    }
}

private fun InputStream.copyTo(out: java.io.OutputStream): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer)
    }
    out.flush()
    return bytesCopied
}

private fun htmlEscape(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
