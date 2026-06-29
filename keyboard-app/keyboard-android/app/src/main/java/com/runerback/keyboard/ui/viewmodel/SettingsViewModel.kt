package com.runerback.keyboard.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.compose.ui.graphics.Color
import com.runerback.keyboard.LogActivity
import com.runerback.keyboard.data.SettingsRepository
import com.runerback.keyboard.network.KeyboardClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _host = MutableStateFlow(SettingsRepository.host.value)
    val host: StateFlow<String> = _host.asStateFlow()

    private val _port = MutableStateFlow(SettingsRepository.port.value)
    val port: StateFlow<String> = _port.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val backgroundColor: StateFlow<Color> = SettingsRepository.backgroundColor
    val interceptRealKeyboard: StateFlow<Boolean> = SettingsRepository.interceptRealKeyboard
    val vibrationEnabled: StateFlow<Boolean> = SettingsRepository.vibrationEnabled
    val vibrationIntensity: StateFlow<Int> = SettingsRepository.vibrationIntensity
    val fKeyOrder: StateFlow<String> = SettingsRepository.fKeyOrder

    val connectionState: StateFlow<KeyboardClient.State> = KeyboardClient.state
    val authState: StateFlow<KeyboardClient.AuthState> = KeyboardClient.authState
    val deviceToken: StateFlow<String> = SettingsRepository.deviceToken

    fun onHostChange(value: String) {
        _host.value = value
        _saved.value = false
    }

    fun onPortChange(value: String) {
        _port.value = value.filter { it.isDigit() }
        _saved.value = false
    }

    fun save() {
        SettingsRepository.setHost(_host.value)
        SettingsRepository.setPort(_port.value)
        _saved.value = true
    }

    fun connect() {
        val hostValue = _host.value.trim()
        val portValue = _port.value.toIntOrNull() ?: 50051
        if (hostValue.isNotEmpty()) {
            KeyboardClient.connect(hostValue, portValue, SettingsRepository.readDeviceToken())
        }
    }

    fun disconnect() {
        KeyboardClient.disconnect()
    }

    fun openLogScreen() {
        val intent = Intent(getApplication(), LogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun resetSavedFlag() {
        _saved.value = false
    }

    fun onBackgroundColorChange(color: Color) {
        SettingsRepository.setBackgroundColor(color)
    }

    fun onInterceptRealKeyboardChange(enabled: Boolean) {
        SettingsRepository.setInterceptRealKeyboard(enabled)
        KeyboardClient.sendConfig(enabled)
    }

    fun onVibrationEnabledChange(enabled: Boolean) {
        SettingsRepository.setVibrationEnabled(enabled)
    }

    fun onVibrationIntensityChange(level: Int) {
        SettingsRepository.setVibrationIntensity(level)
    }

    fun onFKeyOrderChange(order: List<Int>) {
        SettingsRepository.setFKeyOrder(order)
    }
}
