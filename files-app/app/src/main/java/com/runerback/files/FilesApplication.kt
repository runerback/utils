package com.runerback.files

import android.app.Application
import com.runerback.files.ui.components.LogBuffer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FilesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogBuffer.init(this)
        LogBuffer.add("app started")
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LogBuffer.add("FATAL EXCEPTION on thread ${thread.name}: ${throwable.message}")
            throwable.stackTrace.forEach { element ->
                LogBuffer.add("    at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
            }
            throwable.cause?.let { cause ->
                LogBuffer.add("Caused by: ${cause.message}")
                cause.stackTrace.forEach { element ->
                    LogBuffer.add("    at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
                }
            }
            // Chain to the previous/default handler so the system crash dialog still appears.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
