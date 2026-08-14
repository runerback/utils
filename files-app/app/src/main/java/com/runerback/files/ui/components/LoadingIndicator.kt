package com.runerback.files.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

private const val ROTATION_PERIOD_MILLIS = 1000L

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    imageVector: ImageVector = Icons.Default.Refresh,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    var rotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { nanos ->
                val elapsedMillis = (nanos - startTime) / 1_000_000L
                rotation = (elapsedMillis % ROTATION_PERIOD_MILLIS) * 360f / ROTATION_PERIOD_MILLIS
            }
        }
    }

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.rotate(rotation),
        tint = tint,
    )
}
