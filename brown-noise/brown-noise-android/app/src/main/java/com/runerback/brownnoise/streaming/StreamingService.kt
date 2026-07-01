package com.runerback.brownnoise.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.runerback.brownnoise.MainActivity
import com.runerback.brownnoise.R

class StreamingService : Service() {

    companion object {
        const val ACTION_STOP = "com.runerback.brownnoise.STOP_STREAMING"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_VOLUME = "volume"
        private const val CHANNEL_ID = "brown_noise_stream"
        private const val NOTIFICATION_ID = 1
    }

    private val binder = LocalBinder()
    private var audioStreamer: AudioStreamer? = null
    private var onStateChange: ((StreamState) -> Unit)? = null
    private var onWaveform: ((FloatArray) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
        val port = intent.getIntExtra(EXTRA_PORT, 5000)
        val volume = intent.getFloatExtra(EXTRA_VOLUME, 1.0f)

        if (audioStreamer == null) {
            audioStreamer = AudioStreamer(
                onStateChange = { state -> onStateChange?.invoke(state) },
                onAudioData = { frame -> onWaveform?.invoke(frame) }
            )
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        audioStreamer?.start(host, port, volume)
        return START_STICKY
    }

    fun setStateListener(listener: ((StreamState) -> Unit)?) {
        onStateChange = listener
    }

    fun setWaveformListener(listener: ((FloatArray) -> Unit)?) {
        onWaveform = listener
    }

    fun setVolume(volume: Float) {
        audioStreamer?.setVolume(volume)
    }

    fun flushAudio() {
        audioStreamer?.flush()
    }

    fun stopStreaming() {
        audioStreamer?.stop()
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Brown noise streaming"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, StreamingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Streaming brown noise")
            .setSmallIcon(R.drawable.ic_soundwave)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
