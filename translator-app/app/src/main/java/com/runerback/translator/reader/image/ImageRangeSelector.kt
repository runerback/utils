package com.runerback.translator.reader.image

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.max

private enum class SelectedHandle { TOP_LEFT, BOTTOM_RIGHT }

private val HandleSize = 24.dp
private val HandleRadius = 12.dp
private val RectStrokeWidth = 2.dp
private val TranslateButtonWidth = 48.dp
private val TranslateButtonHeight = 32.dp
private val MinRectWidth = max((HandleSize * 2 + TranslateButtonWidth).value, (HandleSize * 2).value).dp
private val MinRectHeight = max((HandleSize * 2 + TranslateButtonHeight).value, (HandleSize * 2).value).dp

@Composable
fun ImageRangeSelector(
    bitmap: Bitmap,
    contentScale: ContentScale = ContentScale.Fit,
    menusVisible: Boolean = false,
    isCropMode: Boolean = false,
    cropRequest: Pair<Int, Offset>? = null,
    onCropModeChanged: (Boolean) -> Unit = {},
    onCrop: (Bitmap, Rect) -> Unit,
) {
    var fullSize by remember { mutableStateOf(Size.Zero) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var selectedHandle by remember { mutableStateOf<SelectedHandle?>(null) }
    var suppressTapsUntil by remember { mutableStateOf(0L) }

    val density = LocalDensity.current
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val topPadding = safePadding.calculateTopPadding()
    val bottomPadding = safePadding.calculateBottomPadding()
    val topPaddingPx = with(density) { topPadding.toPx() }
    val bottomPaddingPx = with(density) { bottomPadding.toPx() }
    val handleRadiusPx = with(density) { HandleRadius.toPx() }
    val buttonHeightPx = with(density) { TranslateButtonHeight.toPx() }
    val minRectWidthPx = with(density) { MinRectWidth.toPx() }.toInt()
    val minRectHeightPx = with(density) { MinRectHeight.toPx() }.toInt()

    val contentSize = remember(fullSize, topPaddingPx, bottomPaddingPx) {
        Size(
            fullSize.width,
            (fullSize.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(0f),
        )
    }

    val layout = remember(bitmap, contentSize, contentScale) {
        computeLayout(contentSize, bitmap, contentScale)
    }

    LaunchedEffect(isCropMode) {
        if (!isCropMode) selectedHandle = null
    }

    fun toContentPoint(point: Offset): Offset {
        return Offset(point.x, point.y - topPaddingPx)
    }

    fun createInitialCropRect(centerX: Float, centerY: Float): Rect {
        val halfW = 100f.coerceAtLeast(minRectWidthPx / 2f)
        val halfH = 60f.coerceAtLeast(minRectHeightPx / 2f)
        val left = (centerX - halfW).toInt().coerceIn(0, contentSize.width.toInt())
        val top = (centerY - halfH).toInt().coerceIn(0, contentSize.height.toInt())
        val right = (centerX + halfW).toInt().coerceIn(0, contentSize.width.toInt())
        val bottom = (centerY + halfH).toInt().coerceIn(0, contentSize.height.toInt())
        return Rect(left, top, right, bottom).coerceIn(contentSize, minRectWidthPx, minRectHeightPx)
    }

    fun hitTestHandle(point: Offset, rect: Rect): SelectedHandle? {
        val topLeftCenter = Offset(rect.left + handleRadiusPx, rect.top + handleRadiusPx)
        val bottomRightCenter = Offset(rect.right - handleRadiusPx, rect.bottom - handleRadiusPx)
        return when {
            (point - topLeftCenter).getDistance() <= handleRadiusPx -> SelectedHandle.TOP_LEFT
            (point - bottomRightCenter).getDistance() <= handleRadiusPx -> SelectedHandle.BOTTOM_RIGHT
            else -> null
        }
    }

    fun moveHandle(handle: SelectedHandle, point: Offset, rect: Rect): Rect {
        val maxX = contentSize.width.toInt()
        val maxY = contentSize.height.toInt()
        return when (handle) {
            SelectedHandle.TOP_LEFT -> {
                val newLeft = point.x.toInt().coerceIn(0, rect.right - minRectWidthPx)
                val newTop = point.y.toInt().coerceIn(0, rect.bottom - minRectHeightPx)
                Rect(newLeft, newTop, rect.right, rect.bottom)
            }
            SelectedHandle.BOTTOM_RIGHT -> {
                val newRight = point.x.toInt().coerceIn(rect.left + minRectWidthPx, maxX)
                val newBottom = point.y.toInt().coerceIn(rect.top + minRectHeightPx, maxY)
                Rect(rect.left, rect.top, newRight, newBottom)
            }
        }
    }

    fun performCrop(rect: Rect) {
        val left = ((rect.left - layout.offset.x) / layout.scale).toInt().coerceIn(0, bitmap.width)
        val top = ((rect.top - layout.offset.y) / layout.scale).toInt().coerceIn(0, bitmap.height)
        val right = ((rect.right - layout.offset.x) / layout.scale).toInt().coerceIn(0, bitmap.width)
        val bottom = ((rect.bottom - layout.offset.y) / layout.scale).toInt().coerceIn(0, bitmap.height)
        if (right > left && bottom > top) {
            val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            // Window-space rect (content coords + top inset) so the translation panel
            // can anchor next to the selected range.
            val windowRect = Rect(
                rect.left,
                (rect.top + topPaddingPx).toInt(),
                rect.right,
                (rect.bottom + topPaddingPx).toInt(),
            )
            onCrop(cropped, windowRect)
        }
    }

    // cropRequest carries a raw window coordinate; the inset conversion into
    // content coordinates is owned by this selector.
    LaunchedEffect(cropRequest, contentSize) {
        if (contentSize.width <= 0f || contentSize.height <= 0f) return@LaunchedEffect
        val point = cropRequest?.second?.let { toContentPoint(it) }
            ?: Offset(contentSize.width / 2f, contentSize.height / 2f)
        cropRect = createInitialCropRect(point.x, point.y)
        selectedHandle = null
        suppressTapsUntil = System.currentTimeMillis() + 300
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { fullSize = it.toSize() }
            .pointerInput(menusVisible) {
                detectTapGestures(
                    onTap = { point ->
                        if (System.currentTimeMillis() < suppressTapsUntil) return@detectTapGestures
                        if (!isCropMode) return@detectTapGestures
                        val rect = cropRect ?: return@detectTapGestures
                        val contentPoint = toContentPoint(point)

                        val hit = hitTestHandle(contentPoint, rect)
                        when {
                            hit != null -> {
                                selectedHandle = if (selectedHandle == hit) null else hit
                            }
                            selectedHandle != null -> {
                                cropRect = moveHandle(selectedHandle!!, contentPoint, rect)
                            }
                            else -> {
                                selectedHandle = null
                                onCropModeChanged(false)
                            }
                        }
                    },
                )
            },
    ) {
        // Selector UI is drawn inside the same padded area where the page image lives.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding),
        ) {
            // Page image (replicated here so the overlay is self-contained and on top).
            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(layout.offset.x, layout.offset.y)
                    scale(layout.scale, layout.scale, pivot = Offset.Zero)
                }) {
                    drawImage(bitmap.asImageBitmap(), topLeft = Offset.Zero)
                }
            }

            cropRect?.let { rect ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                        size = Size(rect.width().toFloat(), rect.height().toFloat()),
                        style = Stroke(width = RectStrokeWidth.value),
                    )
                }

                HandleCircle(
                    selected = selectedHandle == SelectedHandle.TOP_LEFT,
                    modifier = Modifier.align(
                        absoluteOffset(
                            (rect.left + handleRadiusPx).toInt(),
                            (rect.top + handleRadiusPx).toInt(),
                        ),
                    ),
                    onClick = {
                        selectedHandle = if (selectedHandle == SelectedHandle.TOP_LEFT) {
                            null
                        } else {
                            SelectedHandle.TOP_LEFT
                        }
                    },
                )
                HandleCircle(
                    selected = selectedHandle == SelectedHandle.BOTTOM_RIGHT,
                    modifier = Modifier.align(
                        absoluteOffset(
                            (rect.right - handleRadiusPx).toInt(),
                            (rect.bottom - handleRadiusPx).toInt(),
                        ),
                    ),
                    onClick = {
                        selectedHandle = if (selectedHandle == SelectedHandle.BOTTOM_RIGHT) {
                            null
                        } else {
                            SelectedHandle.BOTTOM_RIGHT
                        }
                    },
                )

                Box(
                    modifier = Modifier
                        .align(
                            absoluteOffset(
                                rect.centerX().toInt(),
                                (rect.bottom - buttonHeightPx / 2).toInt(),
                            ),
                        )
                        .size(TranslateButtonWidth, TranslateButtonHeight)
                        .clickable { performCrop(rect) }
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = "Translate",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp),
                    )
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
private fun HandleCircle(
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(HandleSize)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = if (selected) Color.Black else Color.White,
                radius = size.minDimension / 2f,
            )
            drawCircle(
                color = Color.Black,
                radius = size.minDimension / 2f,
                style = Stroke(width = 2f),
            )
        }
    }
}

private fun absoluteOffset(x: Int, y: Int): Alignment {
    return object : Alignment {
        override fun align(size: androidx.compose.ui.unit.IntSize, space: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection): androidx.compose.ui.unit.IntOffset {
            return androidx.compose.ui.unit.IntOffset(x - size.width / 2, y - size.height / 2)
        }
    }
}

private fun Rect.coerceIn(size: Size, minWidth: Int, minHeight: Int): Rect {
    var left = left.coerceIn(0, size.width.toInt())
    var top = top.coerceIn(0, size.height.toInt())
    var right = right.coerceIn(0, size.width.toInt())
    var bottom = bottom.coerceIn(0, size.height.toInt())

    if (right - left < minWidth) {
        if (right < minWidth) {
            left = 0
            right = minWidth
        } else {
            left = right - minWidth
        }
    }
    if (bottom - top < minHeight) {
        if (bottom < minHeight) {
            top = 0
            bottom = minHeight
        } else {
            top = bottom - minHeight
        }
    }
    return Rect(left, top, right, bottom)
}
