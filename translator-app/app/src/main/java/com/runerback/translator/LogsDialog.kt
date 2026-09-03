package com.runerback.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import android.widget.Toast
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.runerback.translator.util.LogManager

private fun Modifier.scrollbar(
    state: ScrollState,
    thickness: Dp = 6.dp,
    color: Color = Color.Black.copy(alpha = 0.35f),
    cornerRadius: Dp = 3.dp,
): Modifier = this.drawWithContent {
    drawContent()
    if (state.maxValue > 0) {
        val thumbThickness = thickness.toPx()
        val thumbHeight =
            (size.height * size.height / (size.height + state.maxValue))
                .coerceIn(thumbThickness, size.height)
        val thumbOffset =
            state.value.toFloat() * (size.height - thumbHeight) / state.maxValue
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - thumbThickness, thumbOffset),
            size = Size(thumbThickness, thumbHeight),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
    }
}

@Composable
fun LogsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val logs by LogManager.logs.collectAsState()
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Logs",
                    color = Color.Black,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = Color.Black,
                    )
                }
            }
        },
        containerColor = Color.White,
        text = {
            SelectionContainer {
                Text(
                    text = logs.ifBlank { "No logs yet." },
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(0.7f)
                        .verticalScroll(scrollState)
                        .scrollbar(scrollState)
                        .padding(4.dp),
                )
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Logs", logs))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy all",
                        tint = Color.Black,
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                OutlinedButton(
                    onClick = {
                        LogManager.clear()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Red,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Clear logs",
                        tint = Color.Red,
                    )
                }
            }
        },
    )
}
