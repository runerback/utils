package com.runerback.files

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.runerback.files.data.settings.AppSettings
import com.runerback.files.ui.components.LogBuffer
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class FilesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        LogBuffer.init(this)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val crashReport = buildCrashReport(thread, throwable)
            try {
                LogBuffer.add(crashReport)
            } catch (e: Exception) {
                // Ignore LogBuffer failures.
            }
            try {
                exportCrashReportToDownloads(crashReport)
            } catch (e: Exception) {
                // Ignore export failures.
            }
            // Chain to the previous/default handler so the system crash dialog still appears.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("================ CRASH REPORT ================")
            appendLine("Timestamp: $timestamp")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine("Exception type: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message}")
            appendLine()
            appendLine("Full stack trace:")
            appendLine(stackTrace)

            appendLine("--- Throwable chain dump ---")
            var current: Throwable? = throwable
            var index = 0
            while (current != null) {
                appendLine("[$index] ${current.javaClass.name}: ${current.message}")
                current.stackTrace.forEach { element ->
                    appendLine("    at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
                }
                if (current.suppressed.isNotEmpty()) {
                    appendLine("    Suppressed exceptions:")
                    current.suppressed.forEach { suppressed ->
                        appendLine("        ${suppressed.javaClass.name}: ${suppressed.message}")
                    }
                }
                current = current.cause
                index++
                if (current != null) appendLine("Caused by:")
            }
            appendLine("============== END CRASH REPORT ==============")
        }
    }

    private fun exportCrashReportToDownloads(report: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val filename = "files_crash_$timestamp.txt"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(report.toByteArray())
                out.flush()
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.apply { mkdirs() }
                ?: return
            val file = File(dir, filename)
            file.writeText(report)
        }
    }
}
