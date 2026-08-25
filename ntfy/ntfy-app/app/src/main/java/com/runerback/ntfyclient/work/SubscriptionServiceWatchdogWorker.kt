package com.runerback.ntfyclient.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.runerback.ntfyclient.data.local.SettingsRepository
import com.runerback.ntfyclient.service.SubscriptionForegroundService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SubscriptionServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settingsRepository = SettingsRepository(applicationContext)
        val enabled = settingsRepository.backgroundListeningEnabled.first()
        Log.d(TAG, "watchdog tick: enabled=$enabled, running=${SubscriptionForegroundService.isRunning.get()}")

        if (enabled && !SubscriptionForegroundService.isRunning.get()) {
            Log.d(TAG, "watchdog restarting subscription service")
            SubscriptionForegroundService.start(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "SubscriptionWatchdog"
        private const val WORK_NAME = "subscription_service_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SubscriptionServiceWatchdogWorker>(15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
