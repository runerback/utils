package com.runerback.imagehelper

import android.app.Application
import com.runerback.imagehelper.util.LogManager

class ImageHelperApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LogManager.init(this)
        LogManager.d("ImageHelperApplication", "onCreate")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LogManager.eSync("ImageHelperApplication", "Uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
