package com.runerback.brownnoise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    state: StreamUiState,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onNoiseTypeChange: (String) -> Unit,
    onGainChange: (Float) -> Unit,
    onSurroundChange: (Float) -> Unit,
    onReverbChange: (Float) -> Unit,
    onSoftnessChange: (Float) -> Unit,
    onWaveChange: (Boolean) -> Unit,
    onWaveRateChange: (Float) -> Unit
) {
    val noiseTypes = listOf("brown", "white", "pink", "tune")
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound settings") },
        confirmButton = {
            TextButton(onClick = onApply) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = state.noiseType.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Noise type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        noiseTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    onNoiseTypeChange(type)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Text("Gain: ${"%.2f".format(state.gain)}")
                Slider(
                    value = state.gain,
                    onValueChange = onGainChange,
                    valueRange = 0.1f..1.5f
                )

                Text("Surround: ${"%.2f".format(state.surround)}")
                Slider(
                    value = state.surround,
                    onValueChange = onSurroundChange,
                    valueRange = 0f..1f
                )

                Text("Reverb: ${"%.2f".format(state.reverb)}")
                Slider(
                    value = state.reverb,
                    onValueChange = onReverbChange,
                    valueRange = 0f..1f
                )

                Text("Softness: ${"%.2f".format(state.softness)}")
                Slider(
                    value = state.softness,
                    onValueChange = onSoftnessChange,
                    valueRange = 0f..1f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Wave")
                    Checkbox(
                        checked = state.wave,
                        onCheckedChange = onWaveChange
                    )
                }

                if (state.wave) {
                    Text("Wave rate: ${"%.2f".format(state.waveRate)}")
                    Slider(
                        value = state.waveRate,
                        onValueChange = onWaveRateChange,
                        valueRange = 0.1f..2f
                    )
                }
            }
        }
    )
}
