package com.runerback.translator.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.withTimeoutOrNull

/** Screen regions a tap maps onto. The collector resolves geometry; the
 *  [ReaderViewModel] owns the meaning. */
enum class ReaderZone { LEFT, RIGHT, TOP, BOTTOM, CENTER }

// Matches the height of the top/bottom settings panels.
private val SettingsZoneHeight = 56.dp
private const val NavZoneWidthFraction = 0.25f

internal fun zoneOf(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    settingsZonePx: Float,
): ReaderZone = when {
    x >= width * (1f - NavZoneWidthFraction) -> ReaderZone.RIGHT
    x < width * NavZoneWidthFraction -> ReaderZone.LEFT
    y < settingsZonePx -> ReaderZone.TOP
    y > height - settingsZonePx -> ReaderZone.BOTTOM
    else -> ReaderZone.CENTER
}

/**
 * The single topmost layer over the reading content. It detects taps and
 * long-presses and forwards them to the [ReaderViewModel] — it makes no
 * decisions itself. Nothing is consumed until a gesture is confirmed, so
 * unconsumed events (scrolling, views underneath) keep working. Interactive
 * overlays (translate toolbar, settings panels, OCR selector, translation
 * panel) sit above this layer and handle their own input.
 */
@Composable
fun ReaderInputLayer(
    onClick: (ReaderZone) -> Unit,
    onLongPress: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Size.Zero) }
    val settingsZonePx = with(density) { SettingsZoneHeight.toPx() }

    val currentWindowOffset by rememberUpdatedState(windowOffset)
    val currentSize by rememberUpdatedState(size)
    val currentSettingsZonePx by rememberUpdatedState(settingsZonePx)

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it.toSize() }
            .onGloballyPositioned { windowOffset = it.positionInWindow() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    when (val result = awaitTapResult(viewConfiguration.longPressTimeoutMillis)) {
                        is TapResult.Up -> {
                            val point = result.change.position
                            onClick(
                                zoneOf(
                                    point.x,
                                    point.y,
                                    currentSize.width,
                                    currentSize.height,
                                    currentSettingsZonePx,
                                ),
                            )
                        }
                        TapResult.Cancelled -> Unit
                        TapResult.TimedOut -> {
                            onLongPress(currentWindowOffset + down.position)
                            // Drain the rest of this gesture so the held finger
                            // that started here doesn't begin the next gesture.
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                            }
                        }
                    }
                }
            },
    )
}

private sealed interface TapResult {
    data class Up(val change: PointerInputChange) : TapResult
    data object Cancelled : TapResult
    data object TimedOut : TapResult
}

/** Waits for tap-up, cancellation (e.g. a scroll stole the gesture), or the
 *  long-press timeout — whichever happens first. Consumes nothing itself. */
private suspend fun AwaitPointerEventScope.awaitTapResult(
    longPressTimeoutMillis: Long,
): TapResult = withTimeoutOrNull(longPressTimeoutMillis) {
    waitForUpOrCancellation()?.let(TapResult::Up) ?: TapResult.Cancelled
} ?: TapResult.TimedOut
