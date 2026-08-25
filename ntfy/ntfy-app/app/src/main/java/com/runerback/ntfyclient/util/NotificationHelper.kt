package com.runerback.ntfyclient.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.runerback.ntfyclient.MainActivity
import com.runerback.ntfyclient.R
import com.runerback.ntfyclient.data.remote.NtfyMessage

object NotificationHelper {

    private const val CHANNEL_ID = "ntfy_messages"
    private const val CHANNEL_NAME = "Ntfy messages"
    const val SERVICE_CHANNEL_ID = "ntfy_service"
    private const val SERVICE_CHANNEL_NAME = "Background service"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }

    fun createServiceChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                SERVICE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }

    fun notify(context: Context, topicName: String, message: NtfyMessage) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("topic", topicName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            topicName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(message.title ?: topicName)
            .setContentText(message.message ?: "")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(message.id.hashCode(), notification)
    }
}
