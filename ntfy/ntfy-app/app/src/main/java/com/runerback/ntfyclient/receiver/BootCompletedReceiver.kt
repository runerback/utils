package com.runerback.ntfyclient.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.runerback.ntfyclient.data.local.SettingsRepository
import com.runerback.ntfyclient.service.SubscriptionForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settingsRepository = SettingsRepository(context)
        val enabled = runBlocking { settingsRepository.backgroundListeningEnabled.first() }
        Log.d(TAG, "boot completed: background listening enabled=$enabled")

        if (enabled) {
            SubscriptionForegroundService.start(context)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
