package com.runerback.remotecp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionPill(isConnected: Boolean) {
    val backgroundColor = if (isConnected) {
        Color(0xFF22c55e).copy(alpha = 0.15f)
    } else {
        Color(0xFFf87171).copy(alpha = 0.15f)
    }
    val textColor = if (isConnected) {
        Color(0xFFbbf7d0)
    } else {
        Color(0xFFfecaca)
    }

    Text(
        text = if (isConnected) "Connected" else "Reconnecting...",
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = textColor,
        style = MaterialTheme.typography.bodyMedium
    )
}
