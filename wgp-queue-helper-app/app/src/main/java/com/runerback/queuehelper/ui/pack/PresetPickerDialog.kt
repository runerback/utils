package com.runerback.queuehelper.ui.pack

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runerback.queuehelper.data.model.Preset
import com.runerback.queuehelper.ui.components.LogBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetPickerDialog(
    presets: List<Preset>,
    initialPresetId: Int?,
    onPresetSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tag = "PresetPickerDialog"
    Log.d(tag, "shown with ${presets.size} presets, initialPresetId=$initialPresetId")
    LogBuffer.add("PresetPickerDialog shown with ${presets.size} presets, initialPresetId=$initialPresetId")

    var expanded by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf(initialPresetId) }
    var searchQuery by remember {
        mutableStateOf(presets.find { it.id == initialPresetId }?.name ?: "")
    }
    Log.d(tag, "initial selectedId=$selectedId, searchQuery=$searchQuery")
    LogBuffer.add("PresetPickerDialog initial selectedId=$selectedId, searchQuery=$searchQuery")

    val filtered = remember(presets, searchQuery) {
        if (searchQuery.isBlank()) {
            presets
        } else {
            presets.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select Preset",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            selectedId = null
                            expanded = true
                        },
                        label = { Text("Search presets") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                        singleLine = true
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        filtered.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    Log.d(tag, "preset selected id=${preset.id}, name=${preset.name}")
                                    LogBuffer.add("PresetPickerDialog preset selected id=${preset.id}, name=${preset.name}")
                                    selectedId = preset.id
                                    searchQuery = preset.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            Log.d(tag, "Create Task clicked, selectedId=$selectedId")
                            LogBuffer.add("PresetPickerDialog Create Task clicked, selectedId=$selectedId")
                            selectedId?.let { onPresetSelected(it) }
                        },
                        enabled = selectedId != null
                    ) {
                        Text("Create Task")
                    }
                }
            }
        }
    }
}
