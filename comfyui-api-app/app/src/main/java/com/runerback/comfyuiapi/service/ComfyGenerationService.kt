package com.runerback.comfyuiapi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.runerback.comfyuiapi.MainActivity
import com.runerback.comfyuiapi.R
import com.runerback.comfyuiapi.data.model.GenerationStatus
import com.runerback.comfyuiapi.data.model.QueueState
import com.runerback.comfyuiapi.data.model.TaskStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ComfyGenerationService : LifecycleService() {

    @Inject
    lateinit var coordinator: GenerationCoordinator

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        coordinator.attachService(lifecycleScope)
        acquireLocks()

        lifecycleScope.launch {
            coordinator.queueState
                .combine(coordinator.generationStatus) { queue, status -> queue to status }
                .collect { (queue, status) ->
                    updateNotification(queue, status)
                    if (queue.items.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = buildNotification(coordinator.queueState.value, coordinator.generationStatus.value)
        startForeground(NOTIFICATION_ID, notification)

        when (intent?.action) {
            ACTION_CANCEL_CURRENT -> coordinator.cancelCurrent()
            ACTION_CANCEL_ALL -> coordinator.cancelAll()
            ACTION_CLEAR_QUEUE -> coordinator.clearQueue()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        coordinator.detachService()
        super.onDestroy()
    }

    private fun updateNotification(queue: QueueState, status: GenerationStatus) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(queue, status))
    }

    private fun buildNotification(queue: QueueState, status: GenerationStatus): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val running = queue.items.firstOrNull { it.status == TaskStatus.Running }
        val queuedCount = queue.items.count { it.status == TaskStatus.Queued }
        val completedCount = queue.items.count { it.status == TaskStatus.Completed }
        val total = queue.items.size.coerceAtLeast(1)
        val progress = running?.progress
        val progressPercent = if (progress != null) {
            ((progress.first.toFloat() / progress.second.coerceAtLeast(1)) * 100).toInt()
        } else {
            ((completedCount.toFloat() / total) * 100).toInt()
        }

        val title = getString(R.string.generation_service_title)
        val content = when (status) {
            is GenerationStatus.Running -> {
                val nodeText = status.currentNode?.let { " • $it" } ?: ""
                val progressText = progress?.let { " (${it.first}/${it.second})" } ?: ""
                "Task ${status.currentQueueIndex ?: running?.index ?: 0} / ${status.queueSize ?: total}$progressText$nodeText"
            }
            is GenerationStatus.Connecting -> getString(R.string.generation_service_connecting)
            is GenerationStatus.Error -> getString(R.string.generation_service_error, status.message)
            is GenerationStatus.Completed -> getString(R.string.generation_service_completed)
            is GenerationStatus.Cancelled -> getString(R.string.generation_service_cancelled)
            is GenerationStatus.Idle -> getString(R.string.generation_service_idle)
        }

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ComfyGenerationService::class.java).apply {
                action = ACTION_CANCEL_ALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, progressPercent.coerceIn(0, 100), progress == null && running != null)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.generation_service_cancel), cancelIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ComfyGenerationService::WakeLock"
            ).apply {
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (_: Exception) {
        }

        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(
                wifiMode,
                "ComfyGenerationService::WifiLock"
            ).apply {
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wakeLock = null

        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wifiLock = null
    }

    companion object {
        const val ACTION_START = "com.runerback.comfyuiapi.action.START_GENERATION"
        const val ACTION_CANCEL_CURRENT = "com.runerback.comfyuiapi.action.CANCEL_CURRENT"
        const val ACTION_CANCEL_ALL = "com.runerback.comfyuiapi.action.CANCEL_ALL"
        const val ACTION_CLEAR_QUEUE = "com.runerback.comfyuiapi.action.CLEAR_QUEUE"

        const val CHANNEL_ID = "comfy_generation"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        fun createNotificationChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.generation_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.generation_channel_description)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
