package com.runerback.tagem.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TagEntity::class, TaggedMediaEntity::class, MediaTagCrossRef::class],
    version = 2,
    exportSchema = false,
)
abstract class TagDatabase : RoomDatabase() {
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: TagDatabase? = null

        fun getInstance(context: Context): TagDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TagDatabase::class.java,
                    "tag_database",
                ).fallbackToDestructiveMigration()
                    .build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
