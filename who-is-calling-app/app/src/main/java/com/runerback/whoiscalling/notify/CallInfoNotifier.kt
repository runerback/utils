package com.runerback.whoiscalling.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.runerback.whoiscalling.MainActivity
import com.runerback.whoiscalling.R
import com.runerback.whoiscalling.data.api.LookupResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallInfoNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun showCallInfo(number: String, result: LookupResult, blocked: Boolean) {
        val title = buildString {
            if (result.isSpam) append("⚠ SPAM · ")
            append(result.sourceName?.takeIf { it.isNotBlank() } ?: number)
        }
        val text = buildList {
            if (result.sourceName != null) add(number)
            result.sourceArea?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (blocked) add("call blocked")
        }.joinToString(" · ")

        notify(number.hashCode(), title, text)
    }

    fun showLookupFailure(number: String, reason: String) {
        notify(number.hashCode(), number, "lookup failed: $reason")
    }

    private fun notify(id: Int, title: String, text: String) {
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setTimeoutAfter(AUTO_CANCEL_MILLIS)
            .setContentIntent(contentIntent)
            .build()
        notificationManager.notify(id, notification)
    }

    companion object {
        const val CHANNEL_ID = "incoming_call_info"
        private const val AUTO_CANCEL_MILLIS = 60_000L

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming call info",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Caller identification results shown while the phone rings"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
