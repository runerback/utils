package com.runerback.brownnoise.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.runerback.brownnoise.ui.DEFAULT_WAVEFORM_SAMPLES
import com.runerback.brownnoise.ui.MAX_WAVEFORM_SAMPLES
import com.runerback.brownnoise.ui.MIN_WAVEFORM_SAMPLES
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object SettingsRepository {

    private lateinit var prefs: SharedPreferences

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    fun init(context: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        _settings.value = loadSettings()
    }

    fun setNoiseType(noiseType: String) {
        prefs.edit().putString(PREF_NOISE_TYPE, noiseType).apply()
        _settings.update { it.copy(noiseType = noiseType) }
    }

    fun setGain(gain: Float) {
        prefs.edit().putFloat(PREF_GAIN, gain).apply()
        _settings.update { it.copy(gain = gain) }
    }

    fun setSurround(surround: Float) {
        prefs.edit().putFloat(PREF_SURROUND, surround).apply()
        _settings.update { it.copy(surround = surround) }
    }

    fun setReverb(reverb: Float) {
        prefs.edit().putFloat(PREF_REVERB, reverb).apply()
        _settings.update { it.copy(reverb = reverb) }
    }

    fun setSoftness(softness: Float) {
        prefs.edit().putFloat(PREF_SOFTNESS, softness).apply()
        _settings.update { it.copy(softness = softness) }
    }

    fun setWave(wave: Boolean) {
        prefs.edit().putBoolean(PREF_WAVE, wave).apply()
        _settings.update { it.copy(wave = wave) }
    }

    fun setWaveRate(waveRate: Float) {
        prefs.edit().putFloat(PREF_WAVE_RATE, waveRate).apply()
        _settings.update { it.copy(waveRate = waveRate) }
    }

    fun setWaveformEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_WAVEFORM_ENABLED, enabled).apply()
        _settings.update { it.copy(waveformEnabled = enabled) }
    }

    fun setWaveformSamples(samples: Int) {
        val clamped = samples.coerceIn(MIN_WAVEFORM_SAMPLES, MAX_WAVEFORM_SAMPLES)
        prefs.edit().putInt(PREF_WAVEFORM_SAMPLES, clamped).apply()
        _settings.update { it.copy(waveformSamples = clamped) }
    }

    private fun loadSettings(): Settings {
        return Settings(
            noiseType = prefs.getString(PREF_NOISE_TYPE, "brown") ?: "brown",
            gain = prefs.getFloat(PREF_GAIN, 0.5f),
            surround = prefs.getFloat(PREF_SURROUND, 0.0f),
            reverb = prefs.getFloat(PREF_REVERB, 0.0f),
            softness = prefs.getFloat(PREF_SOFTNESS, 0.6f),
            wave = prefs.getBoolean(PREF_WAVE, false),
            waveRate = prefs.getFloat(PREF_WAVE_RATE, 0.5f),
            waveformEnabled = prefs.getBoolean(PREF_WAVEFORM_ENABLED, true),
            waveformSamples = prefs.getInt(PREF_WAVEFORM_SAMPLES, DEFAULT_WAVEFORM_SAMPLES)
        )
    }

    private const val PREF_NOISE_TYPE = "noise_type"
    private const val PREF_GAIN = "gain"
    private const val PREF_SURROUND = "surround"
    private const val PREF_REVERB = "reverb"
    private const val PREF_SOFTNESS = "softness"
    private const val PREF_WAVE = "wave"
    private const val PREF_WAVE_RATE = "wave_rate"
    private const val PREF_WAVEFORM_ENABLED = "waveform_enabled"
    private const val PREF_WAVEFORM_SAMPLES = "waveform_samples"
}
