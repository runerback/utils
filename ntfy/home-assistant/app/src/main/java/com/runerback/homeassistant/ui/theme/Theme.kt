package com.runerback.homeassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = PrimaryContainer,
    onSurfaceVariant = Muted,
    outline = Outline,
    error = Error,
    onError = OnPrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Primary.copy(alpha = 0.2f),
    onPrimaryContainer = OnPrimary,
    background = TextPrimary,
    onBackground = Surface,
    surface = TextPrimary.copy(alpha = 0.8f),
    onSurface = Surface,
    surfaceVariant = Primary.copy(alpha = 0.15f),
    onSurfaceVariant = Outline,
    outline = Muted,
    error = Error,
    onError = OnPrimary,
)

@Composable
fun HomeAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
