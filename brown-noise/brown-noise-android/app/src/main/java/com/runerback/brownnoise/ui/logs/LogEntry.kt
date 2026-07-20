package com.runerback.brownnoise.ui.logs

data class LogEntry(
    val level: String,
    val tag: String,
    val message: String,
    val time: Long = System.currentTimeMillis(),
    val throwable: String? = null
)
