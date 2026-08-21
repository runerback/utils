package com.runerback.queuehelper.data.model

import android.net.Uri

/**
 * A reference to an image stored in the media library.
 *
 * [id] is the content hash (SHA-256) and the canonical identifier.
 * [uri] points to the file inside the app's private media directory.
 * [fileName] is the stable name used for the image inside queue.zip.
 */
data class MediaRef(
    val id: String,
    val uri: Uri,
    val fileName: String
)
