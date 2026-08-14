package com.runerback.files.ui.components

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBuffer {

    private const val MAX_LINES = 500
    private const val LOG_FILE_NAME = "comfyui_logs.txt"

    private val lines = mutableListOf<String>()
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null
    private var initialized = false

    fun init(context: Context) {
        synchronized(this) {
            if (initialized) return
            logFile = File(context.filesDir, LOG_FILE_NAME)
            loadFromFile()
            initialized = true
        }
    }

    fun add(message: String) {
        val timestamp = timestampFormat.format(Date())
        val line = "[$timestamp] $message"
        synchronized(lines) {
            lines.add(line)
            if (lines.size > MAX_LINES) {
                lines.removeAt(0)
            }
        }
        appendToFile(line)
    }

    fun getAll(): String {
        synchronized(lines) {
            return lines.joinToString("\n")
        }
    }

    fun clear() {
        synchronized(lines) {
            lines.clear()
        }
        logFile?.delete()
    }

    private fun appendToFile(line: String) {
        val file = logFile ?: return
        try {
            file.appendText("$line\n")
            trimFileIfNeeded(file)
        } catch (e: Exception) {
            // If file logging fails, in-memory logs still work.
        }
    }

    private fun loadFromFile() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            val loaded = file.readLines()
            synchronized(lines) {
                lines.clear()
                lines.addAll(loaded.takeLast(MAX_LINES))
            }
        } catch (e: Exception) {
            file.delete()
        }
    }

    private fun trimFileIfNeeded(file: File) {
        try {
            val loaded = file.readLines()
            if (loaded.size > MAX_LINES) {
                file.writeText(loaded.takeLast(MAX_LINES).joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            // Ignore trim failures.
        }
    }
}
