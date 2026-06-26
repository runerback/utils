package com.runerback.drawer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StrokeWidthSlider(
    label: String = "Stroke Width",
    currentWidth: Float,
    onWidthChanged: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 1f..20f
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ${currentWidth.toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = currentWidth,
            onValueChange = onWidthChanged,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
