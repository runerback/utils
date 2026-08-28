package com.runerback.homeassistant.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LogBuffer {

    private const val MAX_LINES = 200
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val _lines = MutableStateFlow(List(MAX_LINES) { "" })
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    @Synchronized
    fun append(message: String) {
        val timestamp = LocalDateTime.now().format(formatter)
        val current = _lines.value.toMutableList()
        current.add("[$timestamp] $message")
        while (current.size > MAX_LINES) {
            current.removeAt(0)
        }
        _lines.value = current
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
    }

    @Synchronized
    fun getAll(): String = _lines.value.filter { it.isNotBlank() }.joinToString("\n")
}
