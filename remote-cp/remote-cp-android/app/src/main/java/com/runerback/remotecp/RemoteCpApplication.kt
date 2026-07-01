package com.runerback.remotecp

import android.app.Application
import com.runerback.remotecp.util.AppLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RemoteCpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e("Crash", "Uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
