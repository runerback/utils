package com.runerback.screenrecorder.service

import android.content.Context
import android.content.Intent
import com.runerback.screenrecorder.data.RecordingStateRepository

object FloatingControlsCommands {
    fun show(
        context: Context,
        resultCode: Int? = null,
        resultData: Intent? = null,
    ) {
        RecordingStateRepository.setToolboxVisible(true)
        val intent = Intent(context, FloatingControlsService::class.java).apply {
            action = FloatingControlsService.ACTION_SHOW
            if (resultCode != null && resultData != null) {
                putExtra(FloatingControlsService.EXTRA_RESULT_CODE, resultCode)
                putExtra(FloatingControlsService.EXTRA_RESULT_DATA, resultData)
            }
        }
        context.startService(intent)
    }

    fun exit(context: Context) {
        context.startService(
            Intent(context, FloatingControlsService::class.java).apply {
                action = FloatingControlsService.ACTION_EXIT
            },
        )
    }
}
