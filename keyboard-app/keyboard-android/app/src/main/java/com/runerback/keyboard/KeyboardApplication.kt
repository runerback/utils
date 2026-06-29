package com.runerback.keyboard

import android.app.Application
import com.runerback.keyboard.data.SettingsRepository
import com.runerback.keyboard.util.LogManager

class KeyboardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SettingsRepository.init(this)
        LogManager.init(this)
        LogManager.d("KeyboardApplication", "onCreate")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LogManager.eSync("KeyboardApplication", "Uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
