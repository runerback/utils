package com.runerback.screenrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.runerback.screenrecorder.MainActivity
import com.runerback.screenrecorder.R
import com.runerback.screenrecorder.data.FrameRatePreset
import com.runerback.screenrecorder.data.RecorderSettings
import com.runerback.screenrecorder.data.RecordingStateRepository
import com.runerback.screenrecorder.data.ResolutionPreset
import com.runerback.screenrecorder.recorder.ScreenRecordingSession
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenRecorderService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: ScreenRecordingSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (session != null) {
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        if (resultData == null) {
            RecordingStateRepository.setError("Screen capture permission data was missing.")
            stopSelf()
            return
        }
        val settings = RecorderSettings(
            resolutionPreset = ResolutionPreset.fromStorageValue(
                intent.getStringExtra(EXTRA_RESOLUTION_PRESET),
            ),
            frameRatePreset = FrameRatePreset.fromStorageValue(
                intent.getStringExtra(EXTRA_FRAME_RATE_PRESET),
            ),
            captureSystemAudio = intent.getBooleanExtra(EXTRA_CAPTURE_SYSTEM_AUDIO, true),
        )

        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_text_preparing)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        serviceScope.launch {
            try {
                val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
                val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
                session = ScreenRecordingSession(
                    context = this@ScreenRecorderService,
                    mediaProjection = mediaProjection,
                    settings = settings,
                    onStarted = { _, audioActive ->
                        RecordingStateRepository.setRecording(audioActive)
                        updateNotification(getString(R.string.notification_text_recording))
                    },
                    onFinished = { outputUri, _ ->
                        RecordingStateRepository.setIdle(outputUri.toString())
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        session = null
                        stopSelf()
                    },
                    onError = { message ->
                        RecordingStateRepository.setError(message)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        session = null
                        stopSelf()
                    },
                )
                session?.start()
            } catch (exception: IOException) {
                handleStartupFailure(exception.message ?: "Unable to create the recording output.")
            } catch (exception: IllegalStateException) {
                handleStartupFailure(exception.message ?: "Unable to start the recorder.")
            } catch (exception: SecurityException) {
                handleStartupFailure(exception.message ?: "Permission was denied while starting the recorder.")
            }
        }
    }

    private fun handleStop() {
        if (session == null) {
            RecordingStateRepository.setIdle()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        RecordingStateRepository.setStopping()
        serviceScope.launch {
            session?.stop()
            session = null
        }
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenRecorderService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_record)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun handleStartupFailure(message: String) {
        session = null
        RecordingStateRepository.setError(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.runerback.screenrecorder.action.START"
        const val ACTION_STOP = "com.runerback.screenrecorder.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_RESOLUTION_PRESET = "extra_resolution_preset"
        const val EXTRA_FRAME_RATE_PRESET = "extra_frame_rate_preset"
        const val EXTRA_CAPTURE_SYSTEM_AUDIO = "extra_capture_system_audio"

        private const val CHANNEL_ID = "screen-recorder"
        private const val NOTIFICATION_ID = 1001
    }
}
