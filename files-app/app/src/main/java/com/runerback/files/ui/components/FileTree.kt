package com.runerback.files.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.runerback.files.data.model.FileNode

@Composable
fun FileTree(
    nodes: List<FileNode>,
    isLoading: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggle: (FileNode) -> Unit,
    onSelect: (FileNode) -> Unit,
    onToggleSelection: (FileNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val flattened = remember(nodes) { flatten(nodes) }

    if (isLoading && nodes.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(flattened, key = { it.first.id }) { (node, depth) ->
            FileTreeItem(
                node = node,
                depth = depth,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onToggle = onToggle,
                onSelect = onSelect,
                onToggleSelection = onToggleSelection
            )
        }
    }
}

@Composable
private fun FileTreeItem(
    node: FileNode,
    depth: Int,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggle: (FileNode) -> Unit,
    onSelect: (FileNode) -> Unit,
    onToggleSelection: (FileNode) -> Unit
) {
    val startPadding = 8.dp + (depth * 16).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(node) }
            .padding(start = startPadding, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode && !node.isDirectory) {
            Checkbox(
                checked = selectedIds.contains(node.id),
                onCheckedChange = { onToggleSelection(node) },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
        }

        Icon(
            imageVector = when {
                node.isDirectory && node.isExpanded -> Icons.Default.FolderOpen
                node.isDirectory -> Icons.Default.Folder
                else -> Icons.Default.Description
            },
            contentDescription = if (node.isDirectory) "Folder" else "File",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = node.name,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (node.isDirectory) {
            IconButton(
                onClick = { onToggle(node) },
                enabled = !node.isLoading,
                modifier = Modifier.size(24.dp)
            ) {
                if (node.isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        contentDescription = "Loading"
                    )
                } else {
                    Icon(
                        imageVector = if (node.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (node.isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

private fun flatten(nodes: List<FileNode>, depth: Int = 0): List<Pair<FileNode, Int>> {
    return buildList {
        for (node in nodes) {
            add(node to depth)
            if (node.isExpanded && node.children != null) {
                addAll(flatten(node.children, depth + 1))
            }
        }
    }
}
