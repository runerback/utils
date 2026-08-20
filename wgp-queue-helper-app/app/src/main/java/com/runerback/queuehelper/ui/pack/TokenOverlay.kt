package com.runerback.queuehelper.ui.pack

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import com.runerback.queuehelper.data.model.Token
import kotlin.math.max
import kotlin.math.roundToInt

internal data class TokenBounds(
    val token: Token,
    val index: Int,
    val bounds: Rect
)

internal fun computeTokenBounds(
    textLayoutResult: TextLayoutResult,
    visualTokens: List<VisualTokenInfo>
): List<TokenBounds> {
    return visualTokens.mapNotNull { info ->
        val bounds = computeRangeBounds(
            textLayoutResult,
            info.visualRange.first,
            info.visualRange.last + 1
        )
        bounds?.let { TokenBounds(info.token, info.index, it) }
    }
}

private fun computeRangeBounds(
    textLayoutResult: TextLayoutResult,
    start: Int,
    end: Int
): Rect? {
    val clampedStart = start.coerceIn(0, textLayoutResult.layoutInput.text.length)
    val clampedEnd = end.coerceIn(0, textLayoutResult.layoutInput.text.length)
    if (clampedStart >= clampedEnd) return null

    var rect: Rect? = null
    for (i in clampedStart until clampedEnd) {
        val box = textLayoutResult.getBoundingBox(i)
        rect = if (rect == null) {
            box
        } else {
            Rect(
                left = minOf(rect.left, box.left),
                top = minOf(rect.top, box.top),
                right = maxOf(rect.right, box.right),
                bottom = maxOf(rect.bottom, box.bottom)
            )
        }
    }
    return rect
}

@Composable
internal fun TokenOverlay(
    bounds: List<TokenBounds>,
    imageUriFor: (Token.Picture) -> Uri?,
    onTokenClick: (Int, Token) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Box(modifier = modifier) {
        bounds.forEach { tokenBounds ->
            val left = tokenBounds.bounds.left.roundToInt()
            val top = tokenBounds.bounds.top.roundToInt()
            val width = max(1, (tokenBounds.bounds.right - tokenBounds.bounds.left).roundToInt())
            val height = max(1, (tokenBounds.bounds.bottom - tokenBounds.bounds.top).roundToInt())

            val widthDp = with(density) { width.toDp() }
            val heightDp = with(density) { height.toDp() }

            TokenChip(
                token = tokenBounds.token,
                imageUri = (tokenBounds.token as? Token.Picture)?.let(imageUriFor),
                onClick = { onTokenClick(tokenBounds.index, tokenBounds.token) },
                modifier = Modifier
                    .offset { IntOffset(left, top) }
                    .size(width = widthDp, height = heightDp)
            )
        }
    }
}
