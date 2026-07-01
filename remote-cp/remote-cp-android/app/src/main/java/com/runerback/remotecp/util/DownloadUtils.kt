package com.runerback.remotecp.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap

fun Context.saveToDownloads(url: String, fileName: String): Long {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return dm.enqueue(request)
}

fun Context.openDownload(downloadId: Long): Boolean {
    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = dm.getUriForDownloadedFile(downloadId)
    val query = DownloadManager.Query().setFilterById(downloadId)
    var mimeType: String? = null
    var localUri: String? = null
    var title: String? = null
    dm.query(query)?.use { cursor ->
        if (cursor.moveToFirst()) {
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
            localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
        }
    }
    val targetUri = uri ?: localUri?.let { Uri.parse(it) }
    val fileName = title.orEmpty()
    val resolvedMime = resolveMimeType(fileName, mimeType)
    AppLog.i(
        "Download",
        "openDownload id=$downloadId title=$title uri=$targetUri resolvedMime=$resolvedMime rawMime=$mimeType localUri=$localUri"
    )
    if (targetUri == null) {
        AppLog.e("Download", "No URI for download $downloadId")
        return false
    }
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, resolvedMime)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(Intent.createChooser(intent, "Open with"))
        AppLog.i("Download", "Started chooser for $downloadId with mime=$resolvedMime")
        true
    } catch (e: android.content.ActivityNotFoundException) {
        AppLog.e("Download", "No activity found for $downloadId", e)
        false
    } catch (e: Exception) {
        AppLog.e("Download", "Failed to open $downloadId", e)
        false
    }
}

private fun resolveMimeType(fileName: String, rawMime: String?): String {
    if (!rawMime.isNullOrBlank() && rawMime != "application/octet-stream" && rawMime != "*/*") {
        return rawMime
    }
    val extension = fileName.substringAfterLast('.', "")
    if (extension.equals("apk", ignoreCase = true)) {
        return "application/vnd.android.package-archive"
    }
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        ?: rawMime
        ?: "*/*"
}
