package com.runerback.tagem.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
)

@Entity(tableName = "tagged_media")
data class TaggedMediaEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_uri")
    val mediaUri: String,
)

@Entity(
    tableName = "media_tag_cross_ref",
    primaryKeys = ["media_uri", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TaggedMediaEntity::class,
            parentColumns = ["media_uri"],
            childColumns = ["media_uri"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tag_id"]),
    ],
)
data class MediaTagCrossRef(
    @ColumnInfo(name = "media_uri")
    val mediaUri: String,
    @ColumnInfo(name = "tag_id")
    val tagId: Long,
)

data class TagCount(
    @ColumnInfo(name = "media_uri") val mediaUri: String,
    @ColumnInfo(name = "count") val count: Int,
)
