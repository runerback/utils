package com.runerback.brownnoise

import android.app.Application
import com.runerback.brownnoise.ui.logs.AppLogger
import com.runerback.brownnoise.ui.logs.LogBuffer
import com.runerback.brownnoise.ui.settings.SettingsRepository

class BrownNoiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        LogBuffer.init(filesDir)
        AppLogger.i("App", "Application onCreate")

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("App", "Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        try {
            SettingsRepository.init(this)
            AppLogger.i("App", "SettingsRepository initialized")
        } catch (e: Exception) {
            AppLogger.e("App", "Failed to initialize SettingsRepository", e)
        }
    }
}
