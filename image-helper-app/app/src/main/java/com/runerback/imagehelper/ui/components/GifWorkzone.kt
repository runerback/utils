package com.runerback.imagehelper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.runerback.imagehelper.model.FrameTransform
import com.runerback.imagehelper.model.GifFrame
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ONION_SKIN_ALPHA = 0.5f
private const val MAX_ZOOM = 20f

@Composable
fun GifWorkzone(
    frames: List<GifFrame>,
    selectedFrameIndex: Int?,
    gifWidth: Int,
    gifHeight: Int,
    editMode: Boolean,
    onToggleEditMode: (Boolean) -> Unit,
    onTransformChange: (Int, FrameTransform) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val availW = with(density) { maxWidth.toPx() }
        val availH = with(density) { maxHeight.toPx() }
        val fitScale = min(availW / gifWidth, availH / gifHeight)
        val canvasW = gifWidth * fitScale
        val canvasH = gifHeight * fitScale

        val frame = selectedFrameIndex?.let { frames.getOrNull(it) }
        val previousFrame = selectedFrameIndex?.let { frames.getOrNull(it - 1) }
        val decodeSize = (max(canvasW, canvasH) * 2).roundToInt().coerceAtLeast(256)
        val bitmap = rememberDecodedBitmap(frame?.imageUri, decodeSize)
        val previousBitmap = rememberDecodedBitmap(previousFrame?.imageUri, decodeSize)

        val currentFrame by rememberUpdatedState(frame)
        val currentBitmap by rememberUpdatedState(bitmap)
        val currentEditMode by rememberUpdatedState(editMode)
        val canvasBackground = MaterialTheme.colorScheme.surfaceVariant

        fun clampTransform(t: FrameTransform, bmp: ImageBitmap): FrameTransform {
            val baseScale = min(canvasW / bmp.width, canvasH / bmp.height)
            val scale = t.scale.coerceIn(1f, MAX_ZOOM)
            val w = bmp.width * baseScale * scale
            val h = bmp.height * baseScale * scale
            val maxX = max(0f, (w - canvasW) / 2f)
            val maxY = max(0f, (h - canvasH) / 2f)
            return FrameTransform(
                scale = scale,
                offset = Offset(
                    t.offset.x.coerceIn(-maxX, maxX),
                    t.offset.y.coerceIn(-maxY, maxY)
                )
            )
        }

        val canvasModifier = Modifier
            .size(with(density) { canvasW.toDp() }, with(density) { canvasH.toDp() })
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onToggleEditMode(!currentEditMode) }
                )
            }
            .pointerInput(editMode) {
                if (!editMode) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val f = currentFrame ?: return@detectTransformGestures
                    val bmp = currentBitmap ?: return@detectTransformGestures
                    val t = f.transform
                    val newTransform = clampTransform(
                        FrameTransform(scale = t.scale * zoom, offset = t.offset + pan),
                        bmp
                    )
                    selectedFrameIndex?.let { onTransformChange(it, newTransform) }
                }
            }

        Canvas(modifier = canvasModifier) {
            clipRect {
                drawRect(canvasBackground)
                if (editMode && previousFrame != null && previousBitmap != null) {
                    drawFrameImage(previousBitmap, previousFrame.transform, ONION_SKIN_ALPHA)
                }
                if (frame != null && bitmap != null) {
                    drawFrameImage(bitmap, frame.transform, 1f)
                }
            }
        }

        if (frame == null) {
            Text(
                text = if (frames.isEmpty()) "Add images to begin" else "Select a frame",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.drawFrameImage(bmp: ImageBitmap, transform: FrameTransform, alpha: Float) {
    val baseScale = min(size.width / bmp.width, size.height / bmp.height)
    val scale = baseScale * transform.scale
    val w = bmp.width * scale
    val h = bmp.height * scale
    val left = (size.width - w) / 2f + transform.offset.x
    val top = (size.height - h) / 2f + transform.offset.y
    drawImage(
        image = bmp,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bmp.width, bmp.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low
    )
}
