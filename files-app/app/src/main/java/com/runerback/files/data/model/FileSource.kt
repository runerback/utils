package com.runerback.files.data.model

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
sealed class FileSource {
    @Serializable
    data class Local(
        @Serializable(with = UriAsStringSerializer::class)
        val rootUri: Uri
    ) : FileSource()

    @Serializable
    data class Smb(
        val host: String,
        val share: String,
        val username: String,
        val password: String,
        val domain: String = "",
        val rootPath: String = ""
    ) : FileSource()
}
