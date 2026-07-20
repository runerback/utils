package com.runerback.brownnoise.ui.logs

import android.util.Log

object AppLogger {

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        LogBuffer.append(LogEntry("D", tag, message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        LogBuffer.append(LogEntry("I", tag, message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        LogBuffer.append(LogEntry("W", tag, message, throwable = throwable?.stackTraceToString()))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        LogBuffer.append(LogEntry("E", tag, message, throwable = throwable?.stackTraceToString()))
    }
}
