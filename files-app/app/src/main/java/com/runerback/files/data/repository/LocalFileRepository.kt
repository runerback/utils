package com.runerback.files.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.runerback.files.data.model.FileMetadata
import com.runerback.files.data.model.FileNode
import com.runerback.files.data.model.FileSource
import com.runerback.files.ui.components.LogBuffer
import java.io.File
import java.io.IOException
import java.io.InputStream

class LocalFileRepository(
    private val context: Context,
    private val source: FileSource.Local
) : FileRepository {

    private val treeUri: Uri = source.rootUri
    private val isDirectFileAccess = treeUri.scheme == "file"
    private val rootFile: File? = if (isDirectFileAccess) treeUri.path?.let { File(it) } else null

    override suspend fun loadRoot(): Result<FileNode> {
        LogBuffer.add("LocalFileRepository.loadRoot: $treeUri (direct=$isDirectFileAccess)")
        return runCatching {
            if (isDirectFileAccess) {
                val file = rootFile ?: throw IOException("Invalid file path: $treeUri")
                fileToNode(file)
            } else {
                val treeId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
                queryDocument(rootDocumentUri)
                    ?: throw IOException("Cannot load root document for $treeUri")
            }
        }
    }

    override suspend fun listChildren(parentId: String): Result<List<FileNode>> {
        LogBuffer.add("LocalFileRepository.listChildren: $parentId (direct=$isDirectFileAccess)")
        return runCatching {
            if (isDirectFileAccess) {
                val parentFile = File(parentId)
                val files = parentFile.listFiles() ?: emptyArray()
                files
                    .filter { !isHidden(it.name) }
                    .map { fileToNode(it) }
                    .sortedWith(
                        compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() }
                    )
            } else {
                val parentUri = Uri.parse(parentId)
                val parentDocumentId = DocumentsContract.getDocumentId(parentUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
                queryChildren(childrenUri)
            }
        }
    }

    override suspend fun openInputStream(id: String): Result<InputStream> {
        LogBuffer.add("LocalFileRepository.openInputStream: $id")
        return runCatching {
            if (isDirectFileAccess) {
                File(id).inputStream()
            } else {
                context.contentResolver.openInputStream(Uri.parse(id))
                    ?: throw IOException("Cannot open $id")
            }
        }
    }

    override suspend fun createFile(parentId: String, name: String): Result<FileNode> {
        LogBuffer.add("LocalFileRepository.createFile: parent=$parentId name=$name")
        return runCatching {
            if (isDirectFileAccess) {
                val parentFile = File(parentId)
                val file = File(parentFile, name)
                if (!file.createNewFile()) {
                    throw IOException("File already exists: $name")
                }
                fileToNode(file)
            } else {
                val parentUri = Uri.parse(parentId)
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    "text/plain",
                    name
                ) ?: throw IOException("Failed to create file: $name")
                queryDocument(createdUri)
                    ?: throw IOException("Cannot query created file: $createdUri")
            }
        }
    }

    override suspend fun createFolder(parentId: String, name: String): Result<FileNode> {
        LogBuffer.add("LocalFileRepository.createFolder: parent=$parentId name=$name")
        return runCatching {
            if (isDirectFileAccess) {
                val parentFile = File(parentId)
                val folder = File(parentFile, name)
                if (!folder.mkdir()) {
                    throw IOException("Failed to create folder: $name")
                }
                fileToNode(folder)
            } else {
                val parentUri = Uri.parse(parentId)
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name
                ) ?: throw IOException("Failed to create folder: $name")
                queryDocument(createdUri)
                    ?: throw IOException("Cannot query created folder: $createdUri")
            }
        }
    }

    override suspend fun delete(id: String): Result<Unit> {
        LogBuffer.add("LocalFileRepository.delete: $id (direct=$isDirectFileAccess)")
        return runCatching {
            if (isDirectFileAccess) {
                if (!File(id).deleteRecursively()) {
                    throw IOException("Failed to delete: $id")
                }
            } else {
                val uri = Uri.parse(id)
                if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                    throw IOException("Failed to delete: $id")
                }
            }
        }
    }

    private fun fileToNode(file: File): FileNode {
        val displayName = when {
            file.absolutePath == "/storage/emulated/0" -> "Internal storage"
            file.absolutePath == Environment.getExternalStorageDirectory()?.absolutePath -> "Internal storage"
            else -> file.name
        }
        return FileNode(
            id = file.absolutePath,
            name = displayName,
            isDirectory = file.isDirectory,
            metadata = FileMetadata(
                size = if (file.isFile) file.length() else null,
                lastModified = file.lastModified(),
                mimeType = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else guessMimeType(file.name)
            )
        )
    }

    private fun guessMimeType(name: String): String? {
        val extension = name.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "*/*"
    }

    private fun isHidden(name: String): Boolean {
        return name.startsWith('.')
    }

    private fun queryDocument(uri: Uri): FileNode? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                readNodeFromCursor(cursor, uri)
            } else null
        }
    }

    private fun queryChildren(childrenUri: Uri): List<FileNode> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val children = mutableListOf<FileNode>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                )
                val name = cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                )
                if (isHidden(name)) continue
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                readNodeFromCursor(cursor, documentUri)?.let { children.add(it) }
            }
        }
        return children.sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun readNodeFromCursor(cursor: Cursor, documentUri: Uri): FileNode? {
        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: return null else return null
        val mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
        val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        val lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else null

        return FileNode(
            id = documentUri.toString(),
            name = name,
            isDirectory = isDirectory,
            metadata = FileMetadata(
                size = size,
                lastModified = lastModified,
                mimeType = mimeType
            )
        )
    }
}
