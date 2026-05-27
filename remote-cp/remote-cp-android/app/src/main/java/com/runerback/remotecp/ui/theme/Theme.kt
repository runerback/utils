package com.runerback.remotecp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RemoteCpDark = darkColorScheme(
    primary = Color(0xFF38bdf8),
    onPrimary = Color(0xFF0f172a),
    background = Color(0xFF0f172a),
    onBackground = Color(0xFFe5e7eb),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFe5e7eb),
    surfaceVariant = Color(0xFF1f2937),
    onSurfaceVariant = Color(0xFF94a3b8),
    error = Color(0xFFf87171),
    onError = Color(0xFF0f172a),
    outline = Color(0xFF334155),
)

@Composable
fun RemoteCPTheme(content: @Composable () -> Unit) {
    val colorScheme = RemoteCpDark

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
