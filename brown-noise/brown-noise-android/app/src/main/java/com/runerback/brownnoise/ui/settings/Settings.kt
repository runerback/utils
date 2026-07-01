package com.runerback.brownnoise.ui.settings

import com.runerback.brownnoise.ui.DEFAULT_WAVEFORM_SAMPLES

data class Settings(
    val noiseType: String = "brown",
    val gain: Float = 0.5f,
    val surround: Float = 0.0f,
    val reverb: Float = 0.0f,
    val softness: Float = 0.6f,
    val wave: Boolean = false,
    val waveRate: Float = 0.5f,
    val waveformEnabled: Boolean = true,
    val waveformSamples: Int = DEFAULT_WAVEFORM_SAMPLES
)
