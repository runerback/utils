package com.runerback.files.data.repository

import com.runerback.files.data.model.FileNode
import com.runerback.files.data.model.FileSource
import com.runerback.files.ui.components.LogBuffer
import java.io.InputStream

class SMBFileRepository(
    private val source: FileSource.Smb
) : FileRepository {

    init {
        LogBuffer.add("SMBFileRepository created for ${source.host}/${source.share}")
    }

    override suspend fun loadRoot(): Result<FileNode> {
        // TODO: implement SMB root listing using an SMB library such as jcifs-ng.
        // The source object already holds host, share, credentials, domain, and rootPath.
        return Result.failure(NotImplementedError("SMB browsing is not implemented yet"))
    }

    override suspend fun listChildren(parentId: String): Result<List<FileNode>> {
        // TODO: implement SMB child listing.
        return Result.failure(NotImplementedError("SMB browsing is not implemented yet"))
    }

    override suspend fun openInputStream(id: String): Result<InputStream> {
        // TODO: implement SMB file open.
        return Result.failure(NotImplementedError("SMB file open is not implemented yet"))
    }
}
