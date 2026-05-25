package com.runerback.tagem.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaggedMedia(media: TaggedMediaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: MediaTagCrossRef)

    @Delete
    suspend fun deleteCrossRef(crossRef: MediaTagCrossRef)

    @Query("DELETE FROM media_tag_cross_ref WHERE media_uri = :mediaUri AND tag_id = :tagId")
    suspend fun deleteTagFromMedia(mediaUri: String, tagId: Long)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchTags(query: String): Flow<List<TagEntity>>

    @Transaction
    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN media_tag_cross_ref ON tags.id = media_tag_cross_ref.tag_id
        WHERE media_tag_cross_ref.media_uri = :mediaUri
        ORDER BY tags.name ASC
        """,
    )
    fun getTagsForMedia(mediaUri: String): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tagged_media.media_uri FROM tagged_media
        INNER JOIN media_tag_cross_ref ON tagged_media.media_uri = media_tag_cross_ref.media_uri
        WHERE media_tag_cross_ref.tag_id = :tagId
        """,
    )
    fun getMediaUrisForTag(tagId: Long): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM media_tag_cross_ref WHERE media_uri = :mediaUri AND tag_id = :tagId)")
    suspend fun hasTag(mediaUri: String, tagId: Long): Boolean

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM media_tag_cross_ref)")
    suspend fun deleteUnusedTags()

    @Query("SELECT media_uri, COUNT(*) as count FROM media_tag_cross_ref GROUP BY media_uri")
    fun getTagCounts(): Flow<List<TagCount>>
}
