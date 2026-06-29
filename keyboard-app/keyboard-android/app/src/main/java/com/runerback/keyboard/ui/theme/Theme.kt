package com.runerback.keyboard.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.runerback.keyboard.data.SettingsRepository

private val DefaultPrimary = Color(0xFF38bdf8)
private val DefaultOnPrimary = Color(0xFF0f172a)
private val DefaultError = Color(0xFFf87171)
private val DefaultOnError = Color(0xFF0f172a)
private val DefaultOutline = Color(0xFF334155)

private fun Color.shiftLightness(amount: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = (hsl[2] + amount).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun KeyboardTheme(
    content: @Composable () -> Unit
) {
    val baseBackground by SettingsRepository.backgroundColor.collectAsState()

    val colorScheme = remember(baseBackground) {
        val onBackground = if (baseBackground.luminance() > 0.5f) {
            Color(0xFF0f172a)
        } else {
            Color(0xFFe5e7eb)
        }
        darkColorScheme(
            primary = DefaultPrimary,
            onPrimary = DefaultOnPrimary,
            primaryContainer = baseBackground.shiftLightness(0.12f),
            onPrimaryContainer = onBackground,
            secondaryContainer = baseBackground.shiftLightness(0.08f),
            onSecondaryContainer = onBackground,
            background = baseBackground,
            onBackground = onBackground,
            surface = Color(0xFF111827),
            onSurface = Color(0xFFe5e7eb),
            surfaceVariant = Color(0xFF1f2937),
            onSurfaceVariant = Color(0xFF94a3b8),
            error = DefaultError,
            onError = DefaultOnError,
            outline = DefaultOutline,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.background.luminance() > 0.5f
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
