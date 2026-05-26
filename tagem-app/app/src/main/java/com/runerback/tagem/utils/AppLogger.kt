package com.runerback.tagem.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val MAX_LINES = 200
    private val buffer = ArrayDeque<String>(MAX_LINES)
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @JvmStatic
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        append("D", tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val full = if (throwable != null) "$message: ${throwable.message}" else message
        append("E", tag, full)
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append("I", tag, message)
    }

    private fun append(level: String, tag: String, message: String) {
        synchronized(buffer) {
            if (buffer.size >= MAX_LINES) {
                buffer.removeFirst()
            }
            val time = timeFormat.format(Date())
            buffer.addLast("$time $level/$tag: $message")
            _logs.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _logs.value = emptyList()
        }
    }
}
