package com.runerback.keyboard.util

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

object HapticFeedback {

    fun perform(context: Context, enabled: Boolean, intensity: Int) {
        if (!enabled) return

        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            ?: return
        val vibrator = vibratorManager.defaultVibrator
        if (!vibrator.hasVibrator()) return

        val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_PHYSICAL_EMULATION)

        val effect = when {
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) -> {
                val scale = when (intensity) {
                    1 -> 0.30f
                    2 -> 0.45f
                    3 -> 0.60f
                    4 -> 0.75f
                    5 -> 0.90f
                    else -> 1.0f
                }
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
                    .compose()
            }
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) -> {
                val scale = when (intensity) {
                    1 -> 0.25f
                    2 -> 0.40f
                    3 -> 0.55f
                    4 -> 0.70f
                    5 -> 0.85f
                    else -> 1.0f
                }
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                    .compose()
            }
            else -> {
                val duration = when (intensity) {
                    1 -> 5L
                    2 -> 6L
                    3 -> 7L
                    4 -> 8L
                    5 -> 10L
                    else -> 12L
                }
                val amplitude = when (intensity) {
                    1 -> 80
                    2 -> 120
                    3 -> 160
                    4 -> 200
                    5 -> 230
                    else -> 255
                }
                VibrationEffect.createOneShot(duration, amplitude)
            }
        }

        vibrator.vibrate(effect, attributes)
    }
}
