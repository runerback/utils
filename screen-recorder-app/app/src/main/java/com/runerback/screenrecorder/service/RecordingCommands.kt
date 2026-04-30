package com.runerback.screenrecorder.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.runerback.screenrecorder.data.RecorderSettings
import com.runerback.screenrecorder.data.RecorderSettingsRepository
import com.runerback.screenrecorder.data.RecordingStateRepository

object RecordingCommands {
    fun start(
        context: Context,
        resultCode: Int,
        resultData: Intent,
        settings: RecorderSettings = RecorderSettingsRepository.load(context),
    ) {
        RecordingStateRepository.setPreparing()
        val intent = Intent(context, ScreenRecorderService::class.java).apply {
            action = ScreenRecorderService.ACTION_START
            putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, resultData)
            putExtra(
                ScreenRecorderService.EXTRA_RESOLUTION_PRESET,
                settings.resolutionPreset.storageValue,
            )
            putExtra(
                ScreenRecorderService.EXTRA_FRAME_RATE_PRESET,
                settings.frameRatePreset.storageValue,
            )
            putExtra(
                ScreenRecorderService.EXTRA_CAPTURE_SYSTEM_AUDIO,
                settings.captureSystemAudio,
            )
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        RecordingStateRepository.setStopping()
        val intent = Intent(context, ScreenRecorderService::class.java).apply {
            action = ScreenRecorderService.ACTION_STOP
        }
        context.startService(intent)
    }
}
