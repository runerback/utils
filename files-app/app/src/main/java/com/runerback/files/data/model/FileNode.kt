package com.runerback.files.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FileNode(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val isExpanded: Boolean = false,
    val children: List<FileNode>? = null,
    val metadata: FileMetadata = FileMetadata()
)
