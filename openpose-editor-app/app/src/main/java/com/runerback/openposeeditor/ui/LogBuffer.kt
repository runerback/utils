package com.runerback.openposeeditor.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBuffer {

    private val lines = mutableListOf<String>()
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun add(message: String) {
        val timestamp = timestampFormat.format(Date())
        synchronized(lines) {
            lines.add("[$timestamp] $message")
        }
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
    }
}
