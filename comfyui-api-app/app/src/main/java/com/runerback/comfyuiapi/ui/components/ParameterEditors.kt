package com.runerback.comfyuiapi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.runerback.comfyuiapi.R
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun StringFieldEditor(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun IntFieldEditor(
    label: String,
    value: Long,
    min: Long?,
    max: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                it.toLongOrNull()?.let { number ->
                    onValueChange(number)
                }
            },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (min != null && max != null && max > min) {
            Slider(
                value = value.coerceIn(min, max).toFloat(),
                onValueChange = {
                    val rounded = it.roundToInt().toLong()
                    text = rounded.toString()
                    onValueChange(rounded)
                },
                valueRange = min.toFloat()..max.toFloat(),
                steps = (max - min - 1).toInt().coerceAtLeast(0),
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "$min … $max",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun SeedFieldEditor(
    label: String,
    value: Long,
    fixed: Boolean,
    onValueChange: (Long) -> Unit,
    onFixedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                it.toLongOrNull()?.let { number -> onValueChange(number) }
            },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onFixedChange(!fixed) },
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(
                imageVector = if (fixed) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (fixed) "Fixed seed" else "Random seed"
            )
        }
        IconButton(
            onClick = {
                val next = Random.nextLong(0, Long.MAX_VALUE)
                text = next.toString()
                onValueChange(next)
            },
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Randomize"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionFieldEditor(
    label: String,
    options: List<String>,
    selected: String,
    onValueChange: (String) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (options.isEmpty()) {
            OutlinedTextField(
                value = selected,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selected,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh options"
                )
            }
        }
    }
}

@Composable
fun DimensionFieldEditor(
    widthLabel: String,
    heightLabel: String,
    width: Long,
    height: Long,
    min: Long?,
    max: Long?,
    onWidthChange: (Long) -> Unit,
    onHeightChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        IntFieldEditor(
            label = widthLabel,
            value = width,
            min = min,
            max = max,
            onValueChange = onWidthChange,
            modifier = Modifier.weight(1f)
        )
        IntFieldEditor(
            label = heightLabel,
            value = height,
            min = min,
            max = max,
            onValueChange = onHeightChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
}

fun JsonElement.asString(): String = (this as? JsonPrimitive)?.content ?: ""
fun JsonElement.asLong(): Long = (this as? JsonPrimitive)?.longOrNull ?: 0L
