package com.runerback.queuehelper.ui.pack

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineTokenLayoutTest {

    @Test
    fun `text after chip that does not fit moves to next line with full width`() {
        val slots = listOf(
            MeasuredSlot(naturalWidth = 80, naturalHeight = 20, isText = false),
            MeasuredSlot(naturalWidth = 300, naturalHeight = 20, isText = true)
        )

        val (slotInfos, _) = calculateSlotInfos(
            slots = slots,
            maxWidth = 100,
            horizontalSpacingPx = 4,
            textMinWidthPx = 8
        )

        // Chip stays on line 0
        assertEquals(0, slotInfos[0].line)
        assertEquals(0, slotInfos[0].x)
        assertEquals(80, slotInfos[0].width)
        assertEquals(false, slotInfos[0].isText)

        // Text must move to line 1 and use the full line width,
        // not be squeezed into the 16px leftover space on line 0.
        assertEquals(1, slotInfos[1].line)
        assertEquals(0, slotInfos[1].x)
        assertEquals(100, slotInfos[1].width)
        assertEquals(true, slotInfos[1].isText)
    }

    @Test
    fun `plain text shorter than line width stays on one line`() {
        val slots = listOf(
            MeasuredSlot(naturalWidth = 50, naturalHeight = 20, isText = true)
        )

        val (slotInfos, lineHeights) = calculateSlotInfos(
            slots = slots,
            maxWidth = 100,
            horizontalSpacingPx = 4,
            textMinWidthPx = 8
        )

        assertEquals(1, slotInfos.size)
        assertEquals(0, slotInfos[0].line)
        assertEquals(0, slotInfos[0].x)
        assertEquals(50, slotInfos[0].width)
        assertEquals(listOf(20), lineHeights)
    }

    @Test
    fun `long plain text uses full line width`() {
        val slots = listOf(
            MeasuredSlot(naturalWidth = 150, naturalHeight = 40, isText = true)
        )

        val (slotInfos, lineHeights) = calculateSlotInfos(
            slots = slots,
            maxWidth = 100,
            horizontalSpacingPx = 4,
            textMinWidthPx = 8
        )

        assertEquals(1, slotInfos.size)
        assertEquals(0, slotInfos[0].line)
        assertEquals(0, slotInfos[0].x)
        assertEquals(100, slotInfos[0].width)
        assertEquals(listOf(40), lineHeights)
    }

    @Test
    fun `chip that does not fit moves to next line`() {
        val slots = listOf(
            MeasuredSlot(naturalWidth = 60, naturalHeight = 24, isText = false),
            MeasuredSlot(naturalWidth = 60, naturalHeight = 24, isText = false)
        )

        val (slotInfos, _) = calculateSlotInfos(
            slots = slots,
            maxWidth = 100,
            horizontalSpacingPx = 4,
            textMinWidthPx = 8
        )

        assertEquals(2, slotInfos.size)
        assertEquals(0, slotInfos[0].line)
        assertEquals(0, slotInfos[0].x)
        assertEquals(60, slotInfos[0].width)

        assertEquals(1, slotInfos[1].line)
        assertEquals(0, slotInfos[1].x)
        assertEquals(60, slotInfos[1].width)
    }

    @Test
    fun `text chip text layout wraps at chip boundary`() {
        val slots = listOf(
            MeasuredSlot(naturalWidth = 40, naturalHeight = 20, isText = true),
            MeasuredSlot(naturalWidth = 80, naturalHeight = 24, isText = false),
            MeasuredSlot(naturalWidth = 40, naturalHeight = 20, isText = true)
        )

        val (slotInfos, _) = calculateSlotInfos(
            slots = slots,
            maxWidth = 100,
            horizontalSpacingPx = 4,
            textMinWidthPx = 8
        )

        // First text fits on line 0
        assertEquals(0, slotInfos[0].line)
        assertEquals(0, slotInfos[0].x)
        assertEquals(40, slotInfos[0].width)

        // Chip doesn't fit after text (40 + 4 + 80 = 124 > 100), so it moves to line 1
        assertEquals(1, slotInfos[1].line)
        assertEquals(0, slotInfos[1].x)
        assertEquals(80, slotInfos[1].width)

        // Second text doesn't fit on line 1 after chip (80 + 4 + 40 = 124 > 100),
        // so it moves to line 2 with its natural width.
        assertEquals(2, slotInfos[2].line)
        assertEquals(0, slotInfos[2].x)
        assertEquals(40, slotInfos[2].width)
    }

    @Test
    fun `long text after chip is not squeezed into a narrow column`() {
        // Simulates the screenshot issue: text, a wide chip, then a long text segment.
        // The leftover space after the chip is small (76px out of 200px), so the
        // trailing text must move to the next line instead of being squeezed.
        val slots = listOf(
            MeasuredSlot(naturalWidth = 300, naturalHeight = 60, isText = true),
            MeasuredSlot(naturalWidth = 120, naturalHeight = 24, isText = false),
            MeasuredSlot(naturalWidth = 400, naturalHeight = 80, isText = true)
        )

        val (slotInfos, _) = calculateSlotInfos(
            slots = slots,
            maxWidth = 200,
            horizontalSpacingPx = 4,
            textMinWidthPx = 8
        )

        // First long text wraps using the full line width
        assertEquals(0, slotInfos[0].line)
        assertEquals(0, slotInfos[0].x)
        assertEquals(200, slotInfos[0].width)

        // Chip moves to the next line
        assertEquals(1, slotInfos[1].line)
        assertEquals(0, slotInfos[1].x)
        assertEquals(120, slotInfos[1].width)

        // Leftover on line 1 is only 76px (less than half the line width),
        // so the trailing long text must move to line 2 with full width.
        assertEquals(2, slotInfos[2].line)
        assertEquals(0, slotInfos[2].x)
        assertEquals(200, slotInfos[2].width)
    }

    @Test
    fun `short text after chip stays on same line when it fits`() {
        // Simulates "[reference generation] Place <S1> to somewhere."
        // All three segments fit on one line, so the trailing text must NOT
        // be pushed to a new line.
        val slots = listOf(
            MeasuredSlot(naturalWidth = 150, naturalHeight = 20, isText = true),
            MeasuredSlot(naturalWidth = 50, naturalHeight = 24, isText = false),
            MeasuredSlot(naturalWidth = 100, naturalHeight = 20, isText = true)
        )

        val (slotInfos, _) = calculateSlotInfos(
            slots = slots,
            maxWidth = 320,
            horizontalSpacingPx = 4,
            textMinWidthPx = 8
        )

        assertEquals(0, slotInfos[0].line)
        assertEquals(0, slotInfos[0].x)
        assertEquals(150, slotInfos[0].width)

        assertEquals(0, slotInfos[1].line)
        assertEquals(154, slotInfos[1].x)
        assertEquals(50, slotInfos[1].width)

        assertEquals(0, slotInfos[2].line)
        assertEquals(208, slotInfos[2].x)
        assertEquals(100, slotInfos[2].width)
    }

    @Test
    fun `text after wrapped chip wraps in leftover space when leftover is usable`() {
        // First text fills line 0, chip wraps to line 1. Leftover space on line 1
        // is 146px, which is more than half the line width. The trailing text is
        // slightly wider than the leftover, so it stays on line 1 and uses the
        // leftover width instead of jumping to line 2.
        val slots = listOf(
            MeasuredSlot(naturalWidth = 300, naturalHeight = 20, isText = true),
            MeasuredSlot(naturalWidth = 50, naturalHeight = 24, isText = false),
            MeasuredSlot(naturalWidth = 150, naturalHeight = 20, isText = true)
        )

        val (slotInfos, _) = calculateSlotInfos(
            slots = slots,
            maxWidth = 200,
            horizontalSpacingPx = 4,
            textMinWidthPx = 8
        )

        assertEquals(0, slotInfos[0].line)
        assertEquals(0, slotInfos[0].x)
        assertEquals(200, slotInfos[0].width)

        assertEquals(1, slotInfos[1].line)
        assertEquals(0, slotInfos[1].x)
        assertEquals(50, slotInfos[1].width)

        assertEquals(1, slotInfos[2].line)
        assertEquals(54, slotInfos[2].x)
        assertEquals(146, slotInfos[2].width)
    }
}
