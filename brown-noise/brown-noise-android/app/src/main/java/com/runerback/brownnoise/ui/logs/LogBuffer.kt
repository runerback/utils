package com.runerback.brownnoise.ui.logs

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBuffer {

    private const val CAPACITY = 256

    private val buffer = ArrayDeque<LogEntry>(CAPACITY)
    private val lock = Any()

    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(filesDir: File) {
        synchronized(lock) {
            if (logFile == null) {
                logFile = File(filesDir, "logs/app.log").also { it.parentFile?.mkdirs() }
            }
        }
    }

    fun append(entry: LogEntry) {
        synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
            appendToFile(entry)
        }
    }

    fun entries(): List<LogEntry> {
        return synchronized(lock) { buffer.toList() }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            logFile?.writeText("")
        }
    }

    fun readFile(): String {
        return synchronized(lock) {
            logFile?.takeIf { it.exists() }?.readText() ?: ""
        }
    }

    fun filePath(): String? {
        return synchronized(lock) { logFile?.absolutePath }
    }

    private fun appendToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            file.appendText(formatEntry(entry) + "\n")
        } catch (_: Exception) {
        }
    }

    fun formatEntry(entry: LogEntry): String {
        val time = dateFormat.format(Date(entry.time))
        val throwable = entry.throwable?.let { "\n$it" } ?: ""
        return "$time ${entry.level}/${entry.tag}: ${entry.message}$throwable"
    }
}
