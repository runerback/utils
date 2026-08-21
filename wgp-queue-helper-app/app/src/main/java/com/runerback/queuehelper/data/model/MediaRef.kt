package com.runerback.queuehelper.data.model

import android.net.Uri

/**
 * A reference to a media file stored in the media library.
 *
 * [id] is the SHA-256 hash of the source URI string and the canonical identifier.
 * [uri] points to the file inside the app's private media directory.
 * [fileName] is the stable name used for the media inside queue.zip.
 */
data class MediaRef(
    val id: String,
    val uri: Uri,
    val fileName: String
)
