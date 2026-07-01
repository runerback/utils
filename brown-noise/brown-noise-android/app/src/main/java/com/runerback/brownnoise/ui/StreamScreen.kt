package com.runerback.brownnoise.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.brownnoise.ui.settings.SettingsRepository

@Composable
fun StreamScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val settings by SettingsRepository.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Brown Noise Stream",
                style = MaterialTheme.typography.headlineMedium
            )
            Row {
                IconButton(onClick = onNavigateToLogs) {
                    Icon(imageVector = Icons.Default.List, contentDescription = "Logs")
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::onHostChange,
                label = { Text("Host") },
                modifier = Modifier.weight(2f),
                singleLine = true,
                enabled = !state.isPlaying
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = viewModel::onPortChange,
                label = { Text("Port") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.isPlaying
            )
        }

        Button(
            onClick = {
                if (state.isPlaying) viewModel.disconnect() else viewModel.connect()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isPlaying) "Disconnect" else "Connect")
        }

        Text(
            text = "Status: ${state.status}",
            style = MaterialTheme.typography.bodyLarge
        )

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.isPlaying && settings.waveformEnabled && state.waveformPoints.size >= 2) {
            Waveform(points = state.waveformPoints)
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Volume")
            Slider(
                value = state.volume,
                onValueChange = viewModel::onVolumeChange,
                valueRange = 0f..1f
            )
        }
    }
}

@Composable
private fun Waveform(points: List<Float>) {
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2
        val stepX = width / (points.size - 1)
        val path = Path()
        path.moveTo(0f, midY - points[0] * midY)
        for (i in 1 until points.size) {
            path.lineTo(i * stepX, midY - points[i] * midY)
        }
        val strokeWidth = with(density) { 2.dp.toPx() }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth)
        )
    }
}
