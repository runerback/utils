package com.runerback.brownnoise.ui.logs

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LogBuffer {

    private const val CAPACITY = 256

    private val buffer = ArrayDeque<LogEntry>(CAPACITY)
    private val lock = Any()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var logFile: File? = null
    private val dateFormat = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun init(context: Context) {
        synchronized(lock) {
            if (logFile == null) {
                val dir = context.filesDir ?: context.cacheDir
                if (dir != null) {
                    logFile = File(dir, "logs/app.log").also { it.parentFile?.mkdirs() }
                }
            }
        }
    }

    fun append(entry: LogEntry) {
        synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
            _entries.value = buffer.toList()
            appendToFile(entry)
        }
    }

    fun entries(): List<LogEntry> {
        return synchronized(lock) { buffer.toList() }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
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
        val time = dateFormat.format(Instant.ofEpochMilli(entry.time))
        val throwable = entry.throwable?.let { "\n$it" } ?: ""
        return "$time ${entry.level}/${entry.tag}: ${entry.message}$throwable"
    }
}
