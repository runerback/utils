package com.runerback.remotecp.data.model

data class ImageAttachment(
    val name: String,
    val url: String
)

data class VideoAttachment(
    val name: String,
    val url: String
)

data class FileAttachment(
    val name: String,
    val downloadUrl: String
)

data class Message(
    val id: String,
    val text: String,
    val deviceType: String,
    val clientTimestamp: String,
    val images: List<ImageAttachment> = emptyList(),
    val videos: List<VideoAttachment> = emptyList(),
    val files: List<FileAttachment> = emptyList()
)
