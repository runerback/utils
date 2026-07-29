package com.runerback.translator.reader.image

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize

@Composable
fun ImageReaderScreen(
    bitmap: Bitmap,
    contentScale: ContentScale = ContentScale.Fit,
    menusVisible: Boolean = false,
    enterCropMode: Boolean = false,
    onCropModeHandled: () -> Unit = {},
    onCrop: (Bitmap, Rect) -> Unit,
) {
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var isCropMode by remember { mutableStateOf(false) }

    val layout = remember(bitmap, containerSize, contentScale) {
        computeLayout(containerSize, bitmap, contentScale)
    }

    LaunchedEffect(enterCropMode) {
        if (enterCropMode && !isCropMode && !menusVisible) {
            isCropMode = true
            onCropModeHandled()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .onSizeChanged { size ->
                containerSize = size.toSize()
                if (cropRect == null) {
                    val centerX = (containerSize.width / 2).toInt()
                    val centerY = (containerSize.height / 2).toInt()
                    val w = (containerSize.width * 0.4f).toInt()
                    val h = (containerSize.height * 0.2f).toInt()
                    cropRect = Rect(
                        centerX - w / 2,
                        centerY - h / 2,
                        centerX + w / 2,
                        centerY + h / 2,
                    )
                }
            }
            .pointerInput(menusVisible) {
                detectTapGestures(
                    onLongPress = { point ->
                        if (!menusVisible) {
                            isCropMode = true
                            cropRect = Rect(
                                (point.x - 100).toInt().coerceIn(0, containerSize.width.toInt()),
                                (point.y - 60).toInt().coerceIn(0, containerSize.height.toInt()),
                                (point.x + 100).toInt().coerceIn(0, containerSize.width.toInt()),
                                (point.y + 60).toInt().coerceIn(0, containerSize.height.toInt()),
                            )
                        }
                    },
                    onTap = { point ->
                        if (isCropMode) {
                            val rect = cropRect
                            if (rect != null && point.x >= rect.left && point.x <= rect.right && point.y >= rect.top && point.y <= rect.bottom) {
                                val left = ((rect.left - layout.offset.x) / layout.scale).toInt().coerceIn(0, bitmap.width)
                                val top = ((rect.top - layout.offset.y) / layout.scale).toInt().coerceIn(0, bitmap.height)
                                val right = ((rect.right - layout.offset.x) / layout.scale).toInt().coerceIn(0, bitmap.width)
                                val bottom = ((rect.bottom - layout.offset.y) / layout.scale).toInt().coerceIn(0, bitmap.height)
                                if (right > left && bottom > top) {
                                    val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                                    onCrop(cropped, Rect(left, top, right, bottom))
                                }
                            } else {
                                isCropMode = false
                            }
                        }
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(layout.offset.x, layout.offset.y)
                scale(layout.scale, layout.scale, pivot = Offset.Zero)
            }) {
                drawImage(bitmap.asImageBitmap(), topLeft = Offset.Zero)
            }

            if (isCropMode) {
                cropRect?.let { rect ->
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                        size = Size(rect.width().toFloat(), rect.height().toFloat()),
                        style = Stroke(width = 4f),
                    )
                }
            }
        }

        if (isCropMode) {
            cropRect?.let { rect ->
                CornerButton(
                    text = "LT",
                    modifier = Modifier.align(absoluteOffset(rect.left, rect.top)),
                    onDrag = { dx, dy ->
                        cropRect = rect.let {
                            Rect(
                                (it.left + dx).toInt(),
                                (it.top + dy).toInt(),
                                it.right,
                                it.bottom,
                            ).coerceIn(containerSize)
                        }
                    },
                )
                CornerButton(
                    text = "RB",
                    modifier = Modifier.align(absoluteOffset(rect.right, rect.bottom)),
                    onDrag = { dx, dy ->
                        cropRect = rect.let {
                            Rect(
                                it.left,
                                it.top,
                                (it.right + dx).toInt(),
                                (it.bottom + dy).toInt(),
                            ).coerceIn(containerSize)
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        cropRect?.let { rect ->
                            val left = ((rect.left - layout.offset.x) / layout.scale).toInt().coerceIn(0, bitmap.width)
                            val top = ((rect.top - layout.offset.y) / layout.scale).toInt().coerceIn(0, bitmap.height)
                            val right = ((rect.right - layout.offset.x) / layout.scale).toInt().coerceIn(0, bitmap.width)
                            val bottom = ((rect.bottom - layout.offset.y) / layout.scale).toInt().coerceIn(0, bitmap.height)
                            if (right > left && bottom > top) {
                                val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                                onCrop(cropped, Rect(left, top, right, bottom))
                            }
                        }
                    },
                    modifier = Modifier.border(1.dp, Color.Black),
                ) {
                    Text("Translate selection", color = Color.Black)
                }
            }
        }
    }
}

private data class ImageLayout(
    val scale: Float,
    val offset: Offset,
)

private fun computeLayout(containerSize: Size, bitmap: Bitmap, contentScale: ContentScale): ImageLayout {
    if (containerSize.width <= 0 || containerSize.height <= 0) {
        return ImageLayout(1f, Offset.Zero)
    }
    val bitmapRatio = bitmap.width.toFloat() / bitmap.height
    val containerRatio = containerSize.width / containerSize.height
    val scale = when (contentScale) {
        ContentScale.Crop -> kotlin.math.max(
            containerSize.width / bitmap.width,
            containerSize.height / bitmap.height,
        )
        else -> if (containerRatio > bitmapRatio) {
            containerSize.height / bitmap.height
        } else {
            containerSize.width / bitmap.width
        }
    }
    val scaledWidth = bitmap.width * scale
    val scaledHeight = bitmap.height * scale
    val offset = Offset(
        (containerSize.width - scaledWidth) / 2f,
        (containerSize.height - scaledHeight) / 2f,
    )
    return ImageLayout(scale, offset)
}

@Composable
private fun CornerButton(
    text: String,
    modifier: Modifier,
    onDrag: (Float, Float) -> Unit,
) {
    OutlinedButton(
        onClick = {},
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .border(1.dp, Color.Black)
            .background(Color.White),
    ) {
        Text(text, color = Color.Black, fontSize = 12.sp)
    }
}

private fun absoluteOffset(x: Int, y: Int): Alignment {
    return object : Alignment {
        override fun align(size: androidx.compose.ui.unit.IntSize, space: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection): androidx.compose.ui.unit.IntOffset {
            return androidx.compose.ui.unit.IntOffset(x - size.width / 2, y - size.height / 2)
        }
    }
}

private fun Rect.coerceIn(size: Size): Rect {
    return Rect(
        left.coerceIn(0, size.width.toInt()),
        top.coerceIn(0, size.height.toInt()),
        right.coerceIn(0, size.width.toInt()),
        bottom.coerceIn(0, size.height.toInt()),
    )
}
