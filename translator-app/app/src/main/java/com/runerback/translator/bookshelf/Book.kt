package com.runerback.translator.bookshelf

import android.net.Uri
import com.runerback.translator.data.UriSerializer
import kotlinx.serialization.Serializable

enum class BookType {
    EPUB, TXT, PDF, MANGA
}

@Serializable
data class BookEntry(
    @Serializable(with = UriSerializer::class)
    val uri: Uri,
    val name: String,
)

@Serializable
data class Book(
    val id: String,
    val title: String,
    val group: String?,
    val type: BookType,
    @Serializable(with = UriSerializer::class)
    val coverUri: Uri?,
    val entries: List<BookEntry>,
    val lastPage: Int = 0,
    val thumbnailPage: Int = 0,
) {
    val isGroup: Boolean
        get() = group != null
}
