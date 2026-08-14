package com.runerback.files.data.repository

import android.content.Context
import com.runerback.files.data.model.FileSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun create(source: FileSource): FileRepository {
        return when (source) {
            is FileSource.Local -> LocalFileRepository(context, source)
            is FileSource.Smb -> SMBFileRepository(source)
        }
    }
}
