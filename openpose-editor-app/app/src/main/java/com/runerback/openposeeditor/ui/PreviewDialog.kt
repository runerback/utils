package com.runerback.openposeeditor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runerback.openposeeditor.export.OpenPoseExporter
import kotlin.math.roundToInt

@Composable
fun PreviewDialog(
    bitmap: Bitmap?,
    json: String,
    onExportPng: (Bitmap) -> Unit,
    onExportJson: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val hasOffset = offsetX != 0f || offsetY != 0f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Preview", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                }
                bitmap?.let { source ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(source.width.toFloat() / source.height.toFloat())
                            .clipToBounds(),
                    ) {
                        val scale = constraints.maxWidth / source.width.toFloat()
                        Image(
                            bitmap = source.asImageBitmap(),
                            contentDescription = "Rendered pose",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(constraints.maxWidth, constraints.maxHeight) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetX += dragAmount.x / scale
                                        offsetY += dragAmount.y / scale
                                    }
                                }
                                .offset {
                                    IntOffset(
                                        (offsetX * scale).roundToInt(),
                                        (offsetY * scale).roundToInt(),
                                    )
                                },
                        )
                    }
                } ?: Text("Rendering...")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasOffset) {
                        Button(onClick = { offsetX = 0f; offsetY = 0f }) {
                            Text("Reset")
                        }
                    }
                    Button(
                        onClick = {
                            bitmap?.let { source ->
                                onExportPng(shiftBitmap(source, offsetX, offsetY))
                            }
                        },
                        enabled = bitmap != null,
                    ) {
                        Text("Export png")
                    }
                    Button(
                        onClick = {
                            onExportJson(OpenPoseExporter().applyOffset(json, offsetX, offsetY))
                        },
                        enabled = bitmap != null,
                    ) {
                        Text("Export json")
                    }
                }
            }
        }
    }
}

private fun shiftBitmap(source: Bitmap, offsetX: Float, offsetY: Float): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(result).drawBitmap(source, offsetX, offsetY, null)
    return result
}
