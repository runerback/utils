package com.runerback.queuehelper.ui.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBuffer {

    private const val MAX_LINES = 500
    private const val LOG_FILE_NAME = "queue_helper_logs.txt"

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

    fun copyToDownloads(context: Context): String? {
        val file = logFile ?: return null
        if (!file.exists()) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, LOG_FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching null
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                uri.toString()
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dest = File(downloadsDir, LOG_FILE_NAME)
                file.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
        }.getOrElse {
            null
        }
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
