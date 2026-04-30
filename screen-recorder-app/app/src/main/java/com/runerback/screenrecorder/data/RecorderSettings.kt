package com.runerback.screenrecorder.data

import android.content.Context
import androidx.core.content.edit

enum class ResolutionPreset(
    val storageValue: String,
    val label: String,
    val targetLongEdge: Int?,
) {
    HD("hd", "720p", 720),
    FULL_HD("full_hd", "1080p", 1080),
    ORIGINAL("original", "Original", null),
    ;

    companion object {
        fun fromStorageValue(value: String?): ResolutionPreset {
            return entries.firstOrNull { it.storageValue == value } ?: FULL_HD
        }
    }
}

enum class FrameRatePreset(
    val storageValue: String,
    val label: String,
    val framesPerSecond: Int,
) {
    FPS_24("24", "24 FPS", 24),
    FPS_30("30", "30 FPS", 30),
    FPS_60("60", "60 FPS", 60),
    ;

    companion object {
        fun fromStorageValue(value: String?): FrameRatePreset {
            return entries.firstOrNull { it.storageValue == value } ?: FPS_30
        }
    }
}

data class RecorderSettings(
    val resolutionPreset: ResolutionPreset = ResolutionPreset.FULL_HD,
    val frameRatePreset: FrameRatePreset = FrameRatePreset.FPS_30,
    val captureSystemAudio: Boolean = true,
)

object RecorderSettingsRepository {
    private const val PREFERENCES_NAME = "recorder-settings"
    private const val KEY_RESOLUTION_PRESET = "resolution_preset"
    private const val KEY_FRAME_RATE_PRESET = "frame_rate_preset"
    private const val KEY_CAPTURE_SYSTEM_AUDIO = "capture_system_audio"

    fun load(context: Context): RecorderSettings {
        val preferences = preferences(context)
        return RecorderSettings(
            resolutionPreset = ResolutionPreset.fromStorageValue(
                preferences.getString(KEY_RESOLUTION_PRESET, null),
            ),
            frameRatePreset = FrameRatePreset.fromStorageValue(
                preferences.getString(KEY_FRAME_RATE_PRESET, null),
            ),
            captureSystemAudio = preferences.getBoolean(KEY_CAPTURE_SYSTEM_AUDIO, true),
        )
    }

    fun save(context: Context, settings: RecorderSettings) {
        preferences(context).edit {
            putString(KEY_RESOLUTION_PRESET, settings.resolutionPreset.storageValue)
            putString(KEY_FRAME_RATE_PRESET, settings.frameRatePreset.storageValue)
            putBoolean(KEY_CAPTURE_SYSTEM_AUDIO, settings.captureSystemAudio)
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
