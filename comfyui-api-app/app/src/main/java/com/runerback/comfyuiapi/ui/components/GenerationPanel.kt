package com.runerback.comfyuiapi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runerback.comfyuiapi.data.model.GenerationStatus
import com.runerback.comfyuiapi.data.model.QueueState
import com.runerback.comfyuiapi.data.model.TaskStatus
import com.runerback.comfyuiapi.ui.icons.FluentuiSystemIconsDismissSquare
import com.runerback.comfyuiapi.ui.icons.PhosphorQueue

@Composable
fun GenerationPanel(
    status: GenerationStatus,
    queue: QueueState,
    batchCount: Int,
    onBatchCountChange: (Int) -> Unit,
    onGenerateClick: () -> Unit,
    onCancelCurrentClick: () -> Unit,
    onCancelAllQueuedClick: () -> Unit,
    onCancelAllClick: () -> Unit,
    onShowQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = status is GenerationStatus.Connecting || status is GenerationStatus.Running
    val runningItem = queue.items.firstOrNull { it.status == TaskStatus.Running }
    val queuedCount = queue.items.count { it.status == TaskStatus.Queued }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onGenerateClick,
                enabled = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
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
                enabled = true,
                modifier = Modifier.padding(start = 8.dp)
            )

            AnimatedVisibility(visible = isRunning || queuedCount > 0) {
                IconButton(
                    onClick = onShowQueueClick,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = PhosphorQueue,
                        contentDescription = "Queue options"
                    )
                }
            }

            AnimatedVisibility(visible = isRunning) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = FluentuiSystemIconsDismissSquare,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cancel current task") },
                            onClick = {
                                menuExpanded = false
                                onCancelCurrentClick()
                            },
                            enabled = runningItem != null
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel all queued") },
                            onClick = {
                                menuExpanded = false
                                onCancelAllQueuedClick()
                            },
                            enabled = queuedCount > 0
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel all") },
                            onClick = {
                                menuExpanded = false
                                onCancelAllClick()
                            }
                        )
                    }
                }
            }
        }

        when (status) {
            is GenerationStatus.Connecting -> {
                Text("Connecting to server…", modifier = Modifier.padding(top = 8.dp))
            }
            is GenerationStatus.Running -> {
                val taskInfo = buildString {
                    if (status.currentQueueIndex != null && status.queueSize != null) {
                        append("Task ${status.currentQueueIndex} / ${status.queueSize}")
                    }
                    if (status.currentNode != null) {
                        if (isNotEmpty()) append(" — ")
                        append("Node: ${status.currentNode}")
                    }
                }
                if (taskInfo.isNotEmpty()) {
                    Text(
                        text = taskInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            is GenerationStatus.Completed -> {
                Text("Completed", modifier = Modifier.padding(top = 8.dp))
            }
            is GenerationStatus.Cancelled -> {
                Text(
                    text = "Cancelled",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
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
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = count.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.let { onCountChange(it.coerceIn(min, max)) }
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 48.dp, max = 56.dp)
        )
        Column(
            modifier = Modifier.height(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increase batch count",
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = enabled && count < max) {
                        onCountChange((count + 1).coerceAtMost(max))
                    }
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrease batch count",
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = enabled && count > min) {
                        onCountChange((count - 1).coerceAtLeast(min))
                    }
            )
        }
    }
}
