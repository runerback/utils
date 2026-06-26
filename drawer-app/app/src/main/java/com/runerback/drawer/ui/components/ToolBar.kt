package com.runerback.drawer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.drawer.ui.viewmodel.Tool

@Composable
fun ToolBar(
    currentTool: Tool,
    onToolSelected: (Tool) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onShowLogs: () -> Unit,
    bezierMode: Boolean,
    onBezierFinish: () -> Unit,
    onBezierCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bezierMode) {
            IconButton(onClick = onBezierFinish) {
                Icon(Icons.Default.Check, contentDescription = "Finish Bezier")
            }
            IconButton(onClick = onBezierCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Bezier")
            }
        } else {
            ToolButton(Tool.PEN, currentTool, Icons.Default.Edit, "Pen", onToolSelected)
            ToolButton(Tool.BEZIER, currentTool, Icons.Default.Timeline, "Bezier", onToolSelected)
            ToolButton(Tool.SQUARE, currentTool, Icons.Default.CropSquare, "Square", onToolSelected)
            ToolButton(Tool.ROUNDED_RECT, currentTool, Icons.Default.RoundedCorner, "Rounded", onToolSelected)
            ToolButton(Tool.ELLIPSE, currentTool, Icons.Default.Circle, "Ellipse", onToolSelected)
            ToolButton(Tool.FILL, currentTool, Icons.Default.FormatColorFill, "Fill", onToolSelected)

            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = "Clear")
            }
            IconButton(onClick = onShowLogs) {
                Icon(Icons.Default.List, contentDescription = "Logs")
            }
        }
    }
}

@Composable
private fun ToolButton(
    tool: Tool,
    currentTool: Tool,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: (Tool) -> Unit
) {
    val selected = tool == currentTool
    IconButton(onClick = { onClick(tool) }) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
