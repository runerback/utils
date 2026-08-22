package com.runerback.ntfyclient.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object AttachmentScheduler {

    fun schedule(
        context: Context,
        messageId: String,
        url: String,
        name: String,
        unmeteredOnly: Boolean,
    ) {
        val networkType = if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val inputData = Data.Builder()
            .putString(AttachmentDownloadWorker.KEY_MESSAGE_ID, messageId)
            .putString(AttachmentDownloadWorker.KEY_URL, url)
            .putString(AttachmentDownloadWorker.KEY_NAME, name)
            .build()

        val request = OneTimeWorkRequestBuilder<AttachmentDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("attachment_$messageId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "attachment_$messageId",
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
