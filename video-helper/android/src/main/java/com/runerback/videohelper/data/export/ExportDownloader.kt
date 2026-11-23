package com.runerback.videohelper.data.export

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.runerback.videohelper.data.api.VideoHelperApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves rendered exports into the device's public Downloads/Movies collection
 * via DownloadManager (handles MediaStore insertion on all supported SDKs).
 */
@Singleton
class ExportDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: VideoHelperApi,
) {
    /**
     * Enqueue a download of a server-relative path (e.g. "/exports/x.mp4" or the
     * export download endpoint). Returns the DownloadManager download id.
     */
    suspend fun download(serverPath: String, displayName: String): Long {
        val url = api.mediaUrl(serverPath)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(displayName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, displayName)
            .setAllowedOverMetered(true)
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: error("DownloadManager unavailable")
        return manager.enqueue(request)
    }
}
