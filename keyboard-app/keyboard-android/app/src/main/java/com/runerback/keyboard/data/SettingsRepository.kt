package com.runerback.keyboard.data

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsRepository {

    private const val PREFS_NAME = "keyboard_settings"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_DEVICE_TOKEN = "device_token"
    private const val KEY_BACKGROUND_COLOR = "background_color"
    private const val KEY_INTERCEPT_REAL_KEYBOARD = "intercept_real_keyboard"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
    private const val KEY_FKEY_ORDER = "fkey_order"

    private val DEFAULT_BACKGROUND_COLOR = Color.White.toArgb()
    private const val DEFAULT_VIBRATION_INTENSITY = 3
    private val DEFAULT_FKEY_ORDER = (0x70..0x7B).joinToString(",")

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _host = MutableStateFlow("")
    private val _port = MutableStateFlow("50051")
    private val _deviceToken = MutableStateFlow("")
    private val _backgroundColor = MutableStateFlow(Color(DEFAULT_BACKGROUND_COLOR))
    private val _interceptRealKeyboard = MutableStateFlow(false)
    private val _vibrationEnabled = MutableStateFlow(false)
    private val _vibrationIntensity = MutableStateFlow(DEFAULT_VIBRATION_INTENSITY)
    private val _fKeyOrder = MutableStateFlow(DEFAULT_FKEY_ORDER)

    val host: StateFlow<String> = _host.asStateFlow()
    val port: StateFlow<String> = _port.asStateFlow()
    val deviceToken: StateFlow<String> = _deviceToken.asStateFlow()
    val backgroundColor: StateFlow<Color> = _backgroundColor.asStateFlow()
    val interceptRealKeyboard: StateFlow<Boolean> = _interceptRealKeyboard.asStateFlow()
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()
    val vibrationIntensity: StateFlow<Int> = _vibrationIntensity.asStateFlow()
    val fKeyOrder: StateFlow<String> = _fKeyOrder.asStateFlow()

    private val listener = OnSharedPreferenceChangeListener { _, key ->
        mainHandler.post {
            when (key) {
                KEY_HOST -> _host.value = prefs.getString(KEY_HOST, "") ?: ""
                KEY_PORT -> _port.value = prefs.getString(KEY_PORT, "50051") ?: "50051"
                KEY_DEVICE_TOKEN -> _deviceToken.value = prefs.getString(KEY_DEVICE_TOKEN, "") ?: ""
                KEY_BACKGROUND_COLOR -> _backgroundColor.value =
                    Color(prefs.getInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR))
                KEY_INTERCEPT_REAL_KEYBOARD -> _interceptRealKeyboard.value =
                    prefs.getBoolean(KEY_INTERCEPT_REAL_KEYBOARD, false)
                KEY_VIBRATION_ENABLED -> _vibrationEnabled.value =
                    prefs.getBoolean(KEY_VIBRATION_ENABLED, false)
                KEY_VIBRATION_INTENSITY -> _vibrationIntensity.value =
                    prefs.getInt(KEY_VIBRATION_INTENSITY, DEFAULT_VIBRATION_INTENSITY)
                KEY_FKEY_ORDER -> _fKeyOrder.value =
                    prefs.getString(KEY_FKEY_ORDER, DEFAULT_FKEY_ORDER) ?: DEFAULT_FKEY_ORDER
            }
        }
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        refreshAll()
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun refreshAll() {
        _host.value = prefs.getString(KEY_HOST, "") ?: ""
        _port.value = prefs.getString(KEY_PORT, "50051") ?: "50051"
        _deviceToken.value = prefs.getString(KEY_DEVICE_TOKEN, "") ?: ""
        _backgroundColor.value = Color(prefs.getInt(KEY_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR))
        _interceptRealKeyboard.value = prefs.getBoolean(KEY_INTERCEPT_REAL_KEYBOARD, false)
        _vibrationEnabled.value = prefs.getBoolean(KEY_VIBRATION_ENABLED, false)
        _vibrationIntensity.value = prefs.getInt(KEY_VIBRATION_INTENSITY, DEFAULT_VIBRATION_INTENSITY)
        _fKeyOrder.value = prefs.getString(KEY_FKEY_ORDER, DEFAULT_FKEY_ORDER) ?: DEFAULT_FKEY_ORDER
    }

    fun setHost(value: String) {
        prefs.edit().putString(KEY_HOST, value.trim()).apply()
    }

    fun setPort(value: String) {
        prefs.edit().putString(KEY_PORT, value.filter { it.isDigit() }).apply()
    }

    fun setDeviceToken(value: String) {
        prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()
    }

    fun setBackgroundColor(color: Color) {
        prefs.edit().putInt(KEY_BACKGROUND_COLOR, color.toArgb()).apply()
    }

    fun setInterceptRealKeyboard(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTERCEPT_REAL_KEYBOARD, enabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun setVibrationIntensity(level: Int) {
        val clamped = level.coerceIn(1, 6)
        prefs.edit().putInt(KEY_VIBRATION_INTENSITY, clamped).apply()
    }

    fun setFKeyOrder(order: List<Int>) {
        prefs.edit().putString(KEY_FKEY_ORDER, order.joinToString(",")).apply()
    }

    fun readHost(): String = host.value

    fun readPort(): Int = port.value.toIntOrNull() ?: 50051

    fun readInterceptRealKeyboard(): Boolean = interceptRealKeyboard.value

    fun readDeviceToken(): String = deviceToken.value

    fun readFKeyOrder(): List<Int> {
        val parsed = fKeyOrder.value.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0x70..0x7B }
        val default = (0x70..0x7B).toList()
        return if (parsed.size == 12 && parsed.toSet().size == 12) parsed else default
    }
}
