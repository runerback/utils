package com.runerback.drawer

import android.app.Application
import com.runerback.drawer.util.LogManager

class DrawerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LogManager.init(this)
        LogManager.d("DrawerApplication", "onCreate")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LogManager.eSync("DrawerApplication", "Uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
