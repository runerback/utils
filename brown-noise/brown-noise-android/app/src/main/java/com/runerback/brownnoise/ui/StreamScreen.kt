package com.runerback.brownnoise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun StreamScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

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
            IconButton(onClick = { showSettings = true }) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
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

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Volume")
            Slider(
                value = state.volume,
                onValueChange = viewModel::onVolumeChange,
                valueRange = 0f..1f
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onApply = {
                viewModel.applySettings()
                showSettings = false
            },
            onNoiseTypeChange = viewModel::onNoiseTypeChange,
            onGainChange = viewModel::onGainChange,
            onSurroundChange = viewModel::onSurroundChange,
            onReverbChange = viewModel::onReverbChange,
            onSoftnessChange = viewModel::onSoftnessChange,
            onWaveChange = viewModel::onWaveChange,
            onWaveRateChange = viewModel::onWaveRateChange
        )
    }
}
