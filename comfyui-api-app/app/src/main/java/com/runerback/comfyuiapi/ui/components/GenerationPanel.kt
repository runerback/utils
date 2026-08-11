package com.runerback.comfyuiapi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runerback.comfyuiapi.data.model.GenerationStatus

@Composable
fun GenerationPanel(
    status: GenerationStatus,
    batchCount: Int,
    onBatchCountChange: (Int) -> Unit,
    onGenerateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = status is GenerationStatus.Connecting || status is GenerationStatus.Running

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onGenerateClick,
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    when (status) {
                        is GenerationStatus.Connecting -> "Connecting…"
                        is GenerationStatus.Running -> "Running…"
                        else -> "Generate"
                    }
                )
            }

            BatchCountStepper(
                count = batchCount,
                onCountChange = onBatchCountChange,
                enabled = !isRunning,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        when (status) {
            is GenerationStatus.Connecting -> {
                Text("Connecting to server…", modifier = Modifier.padding(top = 8.dp))
            }
            is GenerationStatus.Running -> {
                val progress = status.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.first.toFloat() / progress.second.coerceAtLeast(1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    Text(
                        text = "${progress.first} / ${progress.second}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                val batchInfo = buildString {
                    if (status.currentBatch != null && status.totalBatches != null) {
                        append("Batch ${status.currentBatch} / ${status.totalBatches}")
                    }
                    if (status.currentNode != null) {
                        if (isNotEmpty()) append(" — ")
                        append("Node: ${status.currentNode}")
                    }
                }
                if (batchInfo.isNotEmpty()) {
                    Text(
                        text = batchInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            is GenerationStatus.Completed -> {
                Text("Completed", modifier = Modifier.padding(top = 8.dp))
            }
            is GenerationStatus.Error -> {
                Text(
                    text = "Error: ${status.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun BatchCountStepper(
    count: Int,
    onCountChange: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 20
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { onCountChange((count - 1).coerceAtLeast(min)) },
            enabled = enabled && count > min
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrease batch count"
            )
        }

        OutlinedTextField(
            value = count.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.let { onCountChange(it.coerceIn(min, max)) }
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(min = 56.dp, max = 72.dp)
        )

        IconButton(
            onClick = { onCountChange((count + 1).coerceAtMost(max)) },
            enabled = enabled && count < max
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increase batch count"
            )
        }
    }
}
