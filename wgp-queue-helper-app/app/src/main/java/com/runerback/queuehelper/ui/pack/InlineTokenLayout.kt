package com.runerback.queuehelper.ui.pack

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal sealed class InlineSlot {
    abstract val index: Int
    data class Text(override val index: Int) : InlineSlot()
    data class Chip(override val index: Int) : InlineSlot()
}

internal data class SlotInfo(
    val line: Int,
    val x: Int,
    val width: Int,
    val isText: Boolean
)

internal data class MeasuredSlot(
    val naturalWidth: Int,
    val naturalHeight: Int,
    val isText: Boolean
)

internal fun calculateSlotInfos(
    slots: List<MeasuredSlot>,
    maxWidth: Int,
    horizontalSpacingPx: Int,
    textMinWidthPx: Int
): Pair<List<SlotInfo>, List<Int>> {
    val slotInfos = mutableListOf<SlotInfo>()
    var line = 0
    var currentLineWidth = 0
    var currentLineHeight = 0
    val lineHeights = mutableListOf<Int>()

    slots.forEachIndexed { i, slot ->
        val naturalWidth = slot.naturalWidth
        val spacing = if (currentLineWidth == 0) 0 else horizontalSpacingPx
        val remaining = maxWidth - currentLineWidth - spacing

        when {
            slot.isText -> {
                if (naturalWidth <= remaining) {
                    slotInfos.add(
                        SlotInfo(
                            line = line,
                            x = currentLineWidth + spacing,
                            width = naturalWidth,
                            isText = true
                        )
                    )
                    currentLineWidth += spacing + naturalWidth
                    currentLineHeight = max(currentLineHeight, slot.naturalHeight)
                } else if (currentLineWidth > 0 && remaining >= maxWidth / 2) {
                    // Leftover space is large enough to be usable; let the text wrap
                    // there instead of jumping to a new line and leaving a big gap.
                    slotInfos.add(
                        SlotInfo(
                            line = line,
                            x = currentLineWidth + spacing,
                            width = remaining,
                            isText = true
                        )
                    )
                    lineHeights.add(max(currentLineHeight, slot.naturalHeight))
                    line++
                    currentLineWidth = 0
                    currentLineHeight = 0
                } else {
                    if (currentLineWidth > 0) {
                        lineHeights.add(currentLineHeight)
                        line++
                        currentLineWidth = 0
                        currentLineHeight = 0
                    }
                    val width = min(naturalWidth, maxWidth).coerceAtLeast(textMinWidthPx)
                    slotInfos.add(
                        SlotInfo(
                            line = line,
                            x = 0,
                            width = width,
                            isText = true
                        )
                    )
                    currentLineWidth = width
                    currentLineHeight = slot.naturalHeight
                }
            }
            else -> {
                if (naturalWidth <= remaining) {
                    slotInfos.add(
                        SlotInfo(
                            line = line,
                            x = currentLineWidth + spacing,
                            width = naturalWidth,
                            isText = false
                        )
                    )
                    currentLineWidth += spacing + naturalWidth
                    currentLineHeight = max(currentLineHeight, slot.naturalHeight)
                } else {
                    if (currentLineWidth > 0) {
                        lineHeights.add(currentLineHeight)
                        line++
                        currentLineWidth = 0
                        currentLineHeight = 0
                    }
                    slotInfos.add(
                        SlotInfo(
                            line = line,
                            x = 0,
                            width = naturalWidth,
                            isText = false
                        )
                    )
                    currentLineWidth = naturalWidth
                    currentLineHeight = slot.naturalHeight
                }
            }
        }
    }
    lineHeights.add(currentLineHeight)

    return slotInfos to lineHeights
}

@Composable
internal fun InlineTokenLayout(
    slots: List<InlineSlot>,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 4.dp,
    verticalSpacing: Dp = 0.dp,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    content: @Composable (InlineSlot) -> Unit
) {
    val density = LocalDensity.current
    val horizontalSpacingPx = with(density) { horizontalSpacing.roundToPx() }
    val verticalSpacingPx = with(density) { verticalSpacing.roundToPx() }
    val textMinWidthPx = with(density) { 8.dp.roundToPx() }

    SubcomposeLayout(modifier = modifier) { constraints ->
        val maxWidth = constraints.maxWidth
        val slotCount = slots.size

        if (slotCount == 0 || maxWidth == Constraints.Infinity) {
            val placeables = slots.mapIndexed { index, slot ->
                subcompose(index) { content(slot) }.first().measure(Constraints())
            }
            var x = 0
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val totalWidth = placeables.sumOf { it.width }
            layout(max(constraints.minWidth, totalWidth), height) {
                placeables.forEach { placeable ->
                    placeable.placeRelative(x = x, y = 0)
                    x += placeable.width
                }
            }
        } else {
            val firstPassPlaceables = slots.mapIndexed { index, slot ->
                subcompose("first-$index") { content(slot) }.first().measure(
                    Constraints(minWidth = 0, maxWidth = maxWidth)
                )
            }

            val measurements = slots.mapIndexed { i, slot ->
                MeasuredSlot(
                    naturalWidth = firstPassPlaceables[i].width,
                    naturalHeight = firstPassPlaceables[i].height,
                    isText = slot is InlineSlot.Text
                )
            }

            val (slotInfos, lineHeights) = calculateSlotInfos(
                slots = measurements,
                maxWidth = maxWidth,
                horizontalSpacingPx = horizontalSpacingPx,
                textMinWidthPx = textMinWidthPx
            )

            val finalPlaceables: List<Placeable> = slots.mapIndexed { i, slot ->
                val info = slotInfos[i]
                if (info.isText) {
                    subcompose("final-$i") { content(slot) }.first().measure(
                        Constraints(minWidth = 0, maxWidth = info.width)
                    )
                } else {
                    firstPassPlaceables[i]
                }
            }

            val finalLineHeights = MutableList(lineHeights.size) { 0 }
            slotInfos.forEachIndexed { i, info ->
                finalLineHeights[info.line] = max(finalLineHeights[info.line], finalPlaceables[i].height)
            }

            val visibleLineCount = min(finalLineHeights.size, maxLines)
            val contentHeight = if (visibleLineCount > 0) {
                finalLineHeights.take(visibleLineCount).sum() + (visibleLineCount - 1) * verticalSpacingPx
            } else 0

            val minHeight = if (minLines > visibleLineCount && finalLineHeights.isNotEmpty()) {
                val lineHeightEstimate = finalLineHeights.firstOrNull { it > 0 }
                    ?: finalPlaceables.firstOrNull()?.height
                    ?: 0
                minLines * lineHeightEstimate + (minLines - 1) * verticalSpacingPx
            } else 0
            val totalHeight = max(contentHeight, minHeight)

            layout(maxWidth, totalHeight) {
                val lineY = IntArray(visibleLineCount)
                var y = 0
                for (lineIndex in 0 until visibleLineCount) {
                    lineY[lineIndex] = y
                    y += finalLineHeights[lineIndex] + verticalSpacingPx
                }

                slotInfos.forEachIndexed { i, info ->
                    if (info.line >= visibleLineCount) return@forEachIndexed
                    val placeable = finalPlaceables[i]
                    val lineHeight = finalLineHeights[info.line]
                    val yPos = lineY[info.line] + (lineHeight - placeable.height) / 2
                    placeable.placeRelative(x = info.x, y = yPos)
                }
            }
        }
    }
}
