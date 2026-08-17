package com.runerback.files.data.repository

import com.runerback.files.data.model.FileNode
import java.io.InputStream

interface FileRepository {
    suspend fun loadRoot(): Result<FileNode>
    suspend fun listChildren(parentId: String): Result<List<FileNode>>
    suspend fun openInputStream(id: String): Result<InputStream>
    suspend fun createFile(parentId: String, name: String): Result<FileNode>
}
