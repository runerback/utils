package com.runerback.remotecp.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val LOG_FILE = "app.log"
    private const val MAX_LINES = 500

    private val buffer = ArrayDeque<String>()

    fun init(context: Context) {
        appContext = context.applicationContext
        val file = File(appContext!!.cacheDir, LOG_FILE)
        if (file.exists()) {
            buffer.addAll(file.readLines().takeLast(MAX_LINES))
        }
    }

    fun i(tag: String, message: String) {
        append("I", tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        append("E", tag, message, throwable)
    }

    fun getLines(): List<String> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            appContext?.let { File(it.cacheDir, LOG_FILE).delete() }
        }
    }

    private fun append(level: String, tag: String, message: String, throwable: Throwable?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val line = buildString {
            append("$timestamp $level/$tag: $message")
            throwable?.let {
                append(" | ${it.javaClass.simpleName}: ${it.message}")
            }
        }
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) {
                buffer.removeFirst()
            }
            writeLocked()
        }
    }

    private fun writeLocked() {
        val context = appContext ?: return
        runCatching {
            File(context.cacheDir, LOG_FILE).writeText(buffer.joinToString("\n"))
        }
    }

    private var appContext: Context? = null
}
