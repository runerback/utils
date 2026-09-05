package com.runerback.translator.reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSlicerTest {

    @Test
    fun `uniform lines fill pages greedily and leftover lines form the last page`() {
        // 10 lines of height 10 into 35-high pages: three full pages of 3
        // lines (30 each), last line alone on the final page.
        val slices = slicePages(
            lineCount = 10,
            lineHeight = { 10f },
            availableHeight = 35f,
        )
        assertEquals(listOf(0..2, 3..5, 6..8, 9..9), slices)
    }

    @Test
    fun `exact fit keeps all lines on one page`() {
        val slices = slicePages(
            lineCount = 3,
            lineHeight = { 10f },
            availableHeight = 30f,
        )
        assertEquals(listOf(0..2), slices)
    }

    @Test
    fun `one over the limit starts a new page`() {
        val slices = slicePages(
            lineCount = 4,
            lineHeight = { 10f },
            availableHeight = 30f,
        )
        assertEquals(listOf(0..2, 3..3), slices)
    }

    @Test
    fun `a single line taller than the page gets its own page rather than looping`() {
        val slices = slicePages(
            lineCount = 3,
            lineHeight = { if (it == 0) 100f else 10f },
            availableHeight = 20f,
        )
        // First line overflows alone, then the remaining two fit together.
        assertEquals(listOf(0..0, 1..2), slices)
    }

    @Test
    fun `varying line heights accumulate per line`() {
        // Heights 20, 10, 20, 10 with limit 30: line 2 would push 20+10+20=50
        // over, so page one is lines 0-1; lines 2-3 form page two (20+10=30).
        val slices = slicePages(
            lineCount = 4,
            lineHeight = { if (it % 2 == 0) 20f else 10f },
            availableHeight = 30f,
        )
        assertEquals(listOf(0..1, 2..3), slices)
    }

    @Test
    fun `zero available height still yields one page per line without empty pages`() {
        val slices = slicePages(
            lineCount = 3,
            lineHeight = { 10f },
            availableHeight = 0f,
        )
        assertEquals(listOf(0..0, 1..1, 2..2), slices)
    }

    @Test
    fun `no lines yields no pages`() {
        assertEquals(emptyList<IntRange>(), slicePages(0, { 10f }, 100f))
    }

    // Invariant-based checks: these validate the pagination contract directly
    // (coverage, per-page height limit, greedy optimality) instead of relying
    // on hand-computed page layouts, so they can't silently drift from a
    // miscalculation in either direction.
    @Test
    fun `slices cover every line exactly once in order`() {
        val lineCount = 17
        val slices = slicePages(lineCount, { 10f }, 25f)
        assertEquals((0 until lineCount).toList(), slices.flatMap { it.toList() })
    }

    @Test
    fun `every page fits within the available height`() {
        val available = 25f
        val heights = listOf(10f, 7f, 13f, 10f, 10f, 3f, 10f, 10f, 10f, 5f)
        val slices = slicePages(heights.size, { heights[it] }, available)
        for (slice in slices) {
            val pageHeight = slice.sumOf { heights[it].toDouble() }.toFloat()
            assertTrue(
                "page $slice totals $pageHeight, over limit $available",
                pageHeight <= available,
            )
        }
    }

    @Test
    fun `first line of each later page did not fit on the previous page`() {
        val available = 25f
        val heights = listOf(10f, 7f, 13f, 10f, 10f, 3f, 10f, 10f, 10f, 5f)
        val slices = slicePages(heights.size, { heights[it] }, available)
        for (i in 1 until slices.size) {
            val prevHeight = slices[i - 1].sumOf { heights[it].toDouble() }.toFloat()
            val firstLineNext = heights[slices[i].first]
            assertTrue(
                "line ${slices[i].first} (${firstLineNext}px) should have fit after page ${i - 1} (${prevHeight}px <= $available - $firstLineNext)",
                prevHeight + firstLineNext > available,
            )
        }
    }
}

class WordBoundsTest {

    @Test
    fun `offset inside a word selects that whole word`() {
        assertEquals(6 to 11, findWordBounds("hello world", 8))
    }

    @Test
    fun `offset on the first character selects the word`() {
        assertEquals(6 to 11, findWordBounds("hello world", 6))
    }

    @Test
    fun `offset on the last character selects the word`() {
        assertEquals(6 to 11, findWordBounds("hello world", 10))
    }

    @Test
    fun `offset on whitespace before a word selects the previous word`() {
        // Documented quirk of BreakIterator-based bounds: the boundary at the
        // space resolves backward, so tapping the gap picks the earlier word.
        assertEquals(0 to 6, findWordBounds("cannot all", 6))
    }

    @Test
    fun `offset at start of the second word selects the second word`() {
        assertEquals(7 to 10, findWordBounds("cannot all", 7))
    }

    @Test
    fun `surrounding whitespace is trimmed from the range`() {
        assertEquals(2 to 8, findWordBounds("  padded  ", 4))
    }

    @Test
    fun `punctuation next to a word stays outside the range`() {
        assertEquals(0 to 5, findWordBounds("hello, world", 2))
    }

    @Test
    fun `empty text yields empty range`() {
        assertEquals(0 to 0, findWordBounds("", 0))
    }

    @Test
    fun `offset beyond text length is clamped`() {
        assertEquals(0 to 5, findWordBounds("hello", 99))
    }
}
