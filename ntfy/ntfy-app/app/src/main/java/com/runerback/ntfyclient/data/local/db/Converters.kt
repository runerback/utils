package com.runerback.ntfyclient.data.local.db

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { encodeDefaults = true }

    @TypeConverter
    fun fromTags(tags: String): List<String> =
        if (tags.isBlank()) emptyList() else json.decodeFromString(tags)

    @TypeConverter
    fun toTags(tags: List<String>): String =
        json.encodeToString(tags)

    @TypeConverter
    fun fromDownloadState(state: AttachmentDownloadState): String = state.name

    @TypeConverter
    fun toDownloadState(name: String): AttachmentDownloadState =
        AttachmentDownloadState.entries.find { it.name == name } ?: AttachmentDownloadState.PENDING
}
