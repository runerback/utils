package com.runerback.files.data.repository

import android.webkit.MimeTypeMap
import com.runerback.files.data.model.FileMetadata
import com.runerback.files.data.model.FileNode
import com.runerback.files.data.model.FileSource
import com.runerback.files.ui.components.LogBuffer
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties

class SMBFileRepository(
    private val source: FileSource.Smb
) : FileRepository {

    private val context: CIFSContext by lazy {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val base = BaseContext(PropertyConfiguration(props))
        val auth = NtlmPasswordAuthenticator(source.domain, source.username, source.password)
        base.withCredentials(auth)
    }

    init {
        LogBuffer.add("SMBFileRepository created for ${source.host}/${source.share}")
    }

    override suspend fun loadRoot(): Result<FileNode> = withContext(Dispatchers.IO) {
        runCatching {
            val rootUrl = buildRootUrl()
            val rootFile = SmbFile(rootUrl, context)
            if (!rootFile.exists()) {
                throw IllegalStateException("SMB root does not exist: $rootUrl")
            }
            smbFileToNode(rootFile, isRoot = true)
        }
    }

    override suspend fun listChildren(parentId: String): Result<List<FileNode>> = withContext(Dispatchers.IO) {
        runCatching {
            val parentFile = SmbFile(parentId, context)
            val files = parentFile.listFiles() ?: emptyArray()
            files
                .filter { !isHidden(it.name) }
                .map { smbFileToNode(it, isRoot = false) }
                .sortedWith(
                    compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() }
                )
        }
    }

    override suspend fun openInputStream(id: String): Result<InputStream> = withContext(Dispatchers.IO) {
        runCatching {
            SmbFileInputStream(SmbFile(id, context))
        }
    }

    private fun buildRootUrl(): String {
        val shareName = source.share.ifBlank { "share" }
        val path = source.rootPath.trim('/')
        return if (path.isEmpty()) {
            "smb://${source.host}/$shareName/"
        } else {
            "smb://${source.host}/$shareName/$path/"
        }
    }

    private fun smbFileToNode(file: SmbFile, isRoot: Boolean): FileNode {
        val name = if (isRoot) {
            source.name.ifEmpty { source.share }
        } else {
            file.name.trimEnd('/')
        }
        return FileNode(
            id = file.canonicalPath,
            name = name,
            isDirectory = file.isDirectory,
            metadata = FileMetadata(
                size = if (file.isFile) file.length() else null,
                lastModified = file.lastModified(),
                mimeType = if (file.isDirectory) "*/*" else guessMimeType(file.name)
            )
        )
    }

    private fun guessMimeType(name: String): String? {
        val extension = name.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase())
            ?: "*/*"
    }

    private fun isHidden(name: String): Boolean {
        return name.startsWith('.')
    }
}
