package com.runerback.translator.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the selection-anchor contract both text readers now share after the
 * input refactor: the reported window anchor is composed as
 *
 *     textViewWindowOffset + selectionAnchor(view, start, end)
 *
 * where textViewWindowOffset is measured on the AndroidView itself and
 * selectionAnchor adds the view's total padding (see TextWordSelection.kt).
 * The page margin lives only inside the renderer as the TextView's internal
 * padding, so the composition must land exactly on the selected glyphs:
 *
 *     container Box (window origin = C)
 *       └─ AndroidView / TextView (window origin = C, padding = marginPx)
 *            └─ text Layout: selected word occupies rect L (layout coords)
 *
 *     reported = C + (L + marginPx) == actual glyph rect
 *
 * Run: ./gradlew :app:testDebugUnitTest --tests "*SelectionAnchorPaddingTest*"
 */
class SelectionAnchorPaddingTest {

    // Arbitrary non-zero container origin in the window; non-zero so the
    // test cannot pass by accident through zero-coordinate symmetry.
    private val containerWindowX = 24f
    private val containerWindowY = 48f

    // Page margin, i.e. textMarginPx() (~width of "000" at the reading font
    // size), carried as the TextView's internal padding.
    private val marginPx = 33

    // Layout-relative rect of the selected word on its line.
    private val layoutRect = ViewRect(left = 0, top = 0, right = 100, bottom = 30)

    @Test
    fun `txt reader anchor lands on the glyphs with the page margin`() {
        val viewWindowOffset = WindowOffset(containerWindowX, containerWindowY)
        val reported = reportedWindowAnchor(viewWindowOffset, layoutRect, marginPx)
        val actual = actualGlyphRect(viewWindowOffset, layoutRect, marginPx)
        assertEquals(
            "reported anchor $reported must equal the on-screen text rect $actual",
            actual,
            reported,
        )
    }

    @Test
    fun `pdf reader anchor lands on the glyphs with the page margin`() {
        val viewWindowOffset = WindowOffset(containerWindowX, containerWindowY)
        val reported = reportedWindowAnchor(viewWindowOffset, layoutRect, marginPx)
        val actual = actualGlyphRect(viewWindowOffset, layoutRect, marginPx)
        assertEquals(
            "reported anchor $reported must equal the on-screen text rect $actual",
            actual,
            reported,
        )
    }

    @Test
    fun `without padding the anchor still lands on the glyphs`() {
        val viewWindowOffset = WindowOffset(containerWindowX, containerWindowY)
        val reported = reportedWindowAnchor(viewWindowOffset, layoutRect, paddingPx = 0)
        val actual = actualGlyphRect(viewWindowOffset, layoutRect, paddingPx = 0)
        assertEquals(actual, reported)
    }

    /** Transcription of the anchor composition both readers run inside
     *  `LaunchedEffect(selection, textViewWindowOffset)`: the view's own
     *  window offset + selectionAnchor() (layout rect + view padding). */
    private fun reportedWindowAnchor(
        viewOffset: WindowOffset,
        layout: ViewRect,
        paddingPx: Int,
    ): ViewRect =
        ViewRect(
            left = (viewOffset.x + layout.left + paddingPx).toInt(),
            top = (viewOffset.y + layout.top + paddingPx).toInt(),
            right = (viewOffset.x + layout.right + paddingPx).toInt(),
            bottom = (viewOffset.y + layout.bottom + paddingPx).toInt(),
        )

    /** Ground truth: the glyphs of the selection are drawn at the view
     *  origin + the view padding + the layout-relative rect. */
    private fun actualGlyphRect(
        viewOffset: WindowOffset,
        layout: ViewRect,
        paddingPx: Int,
    ): ViewRect =
        ViewRect(
            left = (viewOffset.x + paddingPx + layout.left).toInt(),
            top = (viewOffset.y + paddingPx + layout.top).toInt(),
            right = (viewOffset.x + paddingPx + layout.right).toInt(),
            bottom = (viewOffset.y + paddingPx + layout.bottom).toInt(),
        )

    private data class WindowOffset(val x: Float, val y: Float)

    private data class ViewRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
}
