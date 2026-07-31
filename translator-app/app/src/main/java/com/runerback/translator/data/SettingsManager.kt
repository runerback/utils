package com.runerback.translator.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Global singleton for in-memory settings state and change events.
 *
 * Values are still persisted through [SettingsRepository] (DataStore), but any
 * composable or Activity can observe and update settings from the same shared
 * object without relying on a single Activity to host the repository.
 */
object SettingsManager {

    private lateinit var repository: SettingsRepository

    private val _readerDebugMode = MutableStateFlow(false)
    val readerDebugMode: StateFlow<Boolean> = _readerDebugMode.asStateFlow()

    fun init(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        if (::repository.isInitialized) return
        repository = SettingsRepository(context.applicationContext)
        scope.launch {
            _readerDebugMode.value = repository.readerDebugMode.first()
            repository.readerDebugMode.collect { value ->
                _readerDebugMode.value = value
            }
        }
    }

    fun setReaderDebugMode(value: Boolean) {
        _readerDebugMode.value = value
        if (::repository.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.setReaderDebugMode(value)
            }
        }
    }
}
