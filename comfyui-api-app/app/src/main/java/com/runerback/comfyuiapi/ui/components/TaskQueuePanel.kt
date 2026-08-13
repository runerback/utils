package com.runerback.comfyuiapi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.runerback.comfyuiapi.data.model.QueueState
import com.runerback.comfyuiapi.data.model.TaskItem
import com.runerback.comfyuiapi.data.model.TaskStatus

@Composable
fun TaskQueueSection(
    queue: QueueState,
    selectedIds: Set<String>,
    onSelect: (String, Boolean) -> Unit,
    onCancelItem: (String) -> Unit,
    onCancelSelected: () -> Unit,
    onCancelAllQueued: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val queuedItems = queue.items.filter { it.status == TaskStatus.Queued }
    val runningItem = queue.items.firstOrNull { it.status == TaskStatus.Running }
    val hasSelection = selectedIds.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse queue" else "Expand queue"
                    )
                    Text(
                        text = "Queue (${queue.items.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (!expanded) {
                    val summary = buildString {
                        if (runningItem != null) append("running #${runningItem.index}")
                        if (queuedItems.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("${queuedItems.size} queued")
                        }
                    }
                    if (summary.isNotEmpty()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onCancelAllQueued,
                    enabled = queuedItems.isNotEmpty()
                ) {
                    Text("Cancel all queued")
                }
            }

            if (expanded) {
                if (hasSelection) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onCancelSelected,
                            enabled = hasSelection
                        ) {
                            Text("Cancel selected")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(queue.items, key = { it.id }) { item ->
                        TaskQueueRow(
                            item = item,
                            selected = selectedIds.contains(item.id),
                            onSelect = { checked -> onSelect(item.id, checked) },
                            onCancel = { onCancelItem(item.id) }
                        )
                        if (item.id != queue.items.last().id) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskQueueDialog(
    queue: QueueState,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (String, Boolean) -> Unit,
    onCancelSelected: () -> Unit,
    onCancelItem: (String) -> Unit,
    onCancelAllQueued: () -> Unit
) {
    val queuedItems = queue.items.filter { it.status == TaskStatus.Queued }
    val hasSelection = selectedIds.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Task queue") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelSelected,
                enabled = hasSelection
            ) {
                Text("Cancel selected")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (queuedItems.isNotEmpty()) {
                    TextButton(
                        onClick = onCancelAllQueued,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel all queued")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(queue.items, key = { it.id }) { item ->
                        TaskQueueRow(
                            item = item,
                            selected = selectedIds.contains(item.id),
                            onSelect = { checked -> onSelect(item.id, checked) },
                            onCancel = { onCancelItem(item.id) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun TaskQueueRow(
    item: TaskItem,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val canSelect = item.status == TaskStatus.Queued
    val statusLabel = when (item.status) {
        is TaskStatus.Queued -> "Queued"
        is TaskStatus.Running -> "Running"
        is TaskStatus.Completed -> "Completed"
        is TaskStatus.Cancelled -> "Cancelled"
        is TaskStatus.Failed -> "Failed"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onSelect(it) },
            enabled = canSelect
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = "Task #${item.index}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.status == TaskStatus.Queued) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel task",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
