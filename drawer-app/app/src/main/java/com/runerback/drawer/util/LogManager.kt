package com.runerback.drawer.util

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {

    private const val MAX_BUFFER_LINES = 500
    private const val LOG_FILE_NAME = "drawer_logs.txt"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val buffer = ArrayDeque<String>()
    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs.asStateFlow()
    private var logFile: File? = null
    private var initialized = false

    fun init(application: Application) {
        if (initialized) return
        logFile = File(application.filesDir, LOG_FILE_NAME)
        loadExistingLogs()
        initialized = true
        d("LogManager", "Initialized, log file: ${logFile?.absolutePath}")
    }

    private fun loadExistingLogs() {
        val file = logFile ?: return
        if (!file.exists()) return
        try {
            file.readLines().takeLast(MAX_BUFFER_LINES).forEach { buffer.addLast(it) }
            _logs.value = buffer.joinToString("\n")
        } catch (e: Exception) {
            Log.e("LogManager", "Failed to load existing logs", e)
        }
    }

    private fun emitLogs() {
        _logs.value = buffer.joinToString("\n")
    }

    private fun formatLine(level: String, tag: String, message: String): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        return "$time $level/$tag: $message"
    }

    private fun buildFullMessage(level: String, tag: String, message: String, throwable: Throwable?): String {
        return buildString {
            append(formatLine(level, tag, message))
            throwable?.let {
                appendLine()
                append(it.stackTraceToString())
            }
        }
    }

    private fun appendLog(fullMessage: String) {
        buffer.addLast(fullMessage)
        while (buffer.size > MAX_BUFFER_LINES) {
            buffer.removeFirst()
        }
        logFile?.appendText("$fullMessage\n")
        emitLogs()
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!initialized) {
            Log.w("LogManager", "Not initialized yet: $message")
        }
        val fullMessage = buildFullMessage(level, tag, message, throwable)

        scope.launch {
            mutex.withLock {
                appendLog(fullMessage)
            }
        }
    }

    private fun writeSync(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!initialized) {
            Log.w("LogManager", "Not initialized yet: $message")
        }
        val fullMessage = buildFullMessage(level, tag, message, throwable)
        runBlocking(Dispatchers.IO) {
            mutex.withLock {
                appendLog(fullMessage)
            }
        }
    }

    fun v(tag: String, message: String) {
        Log.v(tag, message)
        write("V", tag, message)
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        write("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("I", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        write("W", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        write("E", tag, message, throwable)
    }

    fun eSync(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        writeSync("E", tag, message, throwable)
    }

    suspend fun getRecentLogs(): String = mutex.withLock {
        buffer.joinToString("\n")
    }

    fun getRecentLogsBlocking(): String = buffer.joinToString("\n")

    fun clear() {
        scope.launch {
            mutex.withLock {
                buffer.clear()
                logFile?.delete()
                emitLogs()
            }
        }
    }
}
