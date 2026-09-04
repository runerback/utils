package com.runerback.translator.reader.text

import com.runerback.translator.reader.epub.HtmlCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReflowTest {

    // region txt

    @Test
    fun `hard-wrapped prose lines are joined into one paragraph`() {
        val extracted = """
            reach the mists of time; and yet four lives of
            ordinary duration would suffice to transmit,
            from mouth to mouth, in the form of tradition,
            all
            that civilized man has achieved within the
        """.trimIndent()

        val result = normalizeForReflow(extracted)

        assertEquals(
            "reach the mists of time; and yet four lives of ordinary duration would suffice to transmit, from mouth to mouth, in the form of tradition, all that civilized man has achieved within the",
            result,
        )
    }

    @Test
    fun `sentence boundary starts a new line even without a blank line`() {
        val extracted = """
            consider it solely in connection with time.
            This glance into the perspective of the past
            will prepare the reader to look at the pictures
        """.trimIndent()

        val result = normalizeForReflow(extracted)

        assertEquals(
            "consider it solely in connection with time.\nThis glance into the perspective of the past will prepare the reader to look at the pictures",
            result,
        )
    }

    @Test
    fun `verse lines that start with capitals keep their breaks`() {
        val extracted = """
            The woods are lovely, dark and deep,
            But I have promises to keep,
            And miles to go before I sleep,
            And miles to go before I sleep.
        """.trimIndent()

        assertEquals(extracted, normalizeForReflow(extracted))
    }

    @Test
    fun `headings and numbered list items keep their breaks`() {
        val extracted = """
            CHAPTER I
            The Project Gutenberg eBook
            1. First item
            2. Second item
        """.trimIndent()

        assertEquals(extracted, normalizeForReflow(extracted))
    }

    @Test
    fun `dialogue lines keep their breaks`() {
        val extracted = """
            He rose slowly and said:
            "I will think of it."
            Then he left the room.
        """.trimIndent()

        assertEquals(extracted, normalizeForReflow(extracted))
    }

    @Test
    fun `hyphenated word split across lines is rejoined without the hyphen`() {
        assertEquals("a consideration of things", normalizeForReflow("a considera-\ntion of things"))
    }

    @Test
    fun `natural break at an existing hyphen also loses the hyphen`() {
        // Known limitation: "well-\nknown" looks identical to wrap-inserted
        // hyphenation, so rejoining always drops the hyphen.
        assertEquals("wellknown", normalizeForReflow("well-\nknown"))
    }

    @Test
    fun `lowercase continuation after an opening quote is joined`() {
        assertEquals("he said, \"the project of doom\"", normalizeForReflow("he said, \"the\nproject of doom\""))
    }

    @Test
    fun `CJK line breaks are preserved`() {
        val extracted = "第一行内容\n第二行内容\n第三行内容"
        assertEquals(extracted, normalizeForReflow(extracted))
    }

    @Test
    fun `windows and legacy mac line endings are normalized`() {
        assertEquals("a b c", normalizeForReflow("a\r\nb\rc"))
    }

    @Test
    fun `tabs and repeated spaces collapse to single spaces`() {
        assertEquals("a b c", normalizeForReflow("a \t  b\t\tc"))
    }

    @Test
    fun `blank lines separate paragraphs and runs of blank lines collapse to one`() {
        val extracted = """
            paragraph one line one
            paragraph one line two


            paragraph two line one
        """.trimIndent()

        assertEquals(
            "paragraph one line one paragraph one line two\n\nparagraph two line one",
            normalizeForReflow(extracted),
        )
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals("a\n\nb", normalizeForReflow("  a  \n\n  b  "))
    }

    @Test
    fun `normalization is idempotent`() {
        val once = normalizeForReflow("one\ntwo\n\nthree\nfour")
        assertEquals(once, normalizeForReflow(once))
    }

    // endregion

    // region pdf

    @Test
    fun `pdf-extracted hard-wrapped page reflows into flowing paragraphs`() {
        // Text as extracted from a PDF page: hard line breaks at the source
        // column width, no blank lines between paragraphs, stray one-word lines.
        val extracted = """
            reach the mists of time; and yet four lives of
            ordinary duration would suffice to transmit,
            from mouth to mouth, in the form of tradition,
            all
            that civilized man has achieved within the
            limits of the republic. Although New York
            alone
            possesses a population materially exceeding
            that of either of the four smallest kingdoms of
            Europe, or materially exceeding that of the
            entire Swiss Confederation, it is little more
            than
            two centuries since the Dutch commenced
            their settlement, rescuing the region from the
            savage state. Thus, what seems venerable by
            an accumulation of changes is reduced to
            familiarity when we come seriously to
            consider it solely in connection with time.
            This glance into the perspective of the past
            will prepare the reader to look at the pictures
            we
            are about to sketch, with less surprise than he
            might otherwise feel; and a few additional
            explanations may carry him back in
            imagination to the precise condition of society
            that we
            desire to delineate.
        """.trimIndent()

        val result = normalizeForReflow(extracted)

        assertEquals(
            "reach the mists of time; and yet four lives of ordinary duration would suffice to transmit, from mouth to mouth, in the form of tradition, all that civilized man has achieved within the limits of the republic. Although New York alone possesses a population materially exceeding that of either of the four smallest kingdoms of\nEurope, or materially exceeding that of the entire Swiss Confederation, it is little more than two centuries since the Dutch commenced their settlement, rescuing the region from the savage state. Thus, what seems venerable by an accumulation of changes is reduced to familiarity when we come seriously to consider it solely in connection with time.\nThis glance into the perspective of the past will prepare the reader to look at the pictures we are about to sketch, with less surprise than he might otherwise feel; and a few additional explanations may carry him back in imagination to the precise condition of society that we desire to delineate.",
            result,
        )
    }

    // endregion

    // region epub

    @Test
    fun `epub source newlines inside paragraph are collapsed`() {
        val html = """
            <html><body>
            <p>It is believed that the scene of this tale,
            and most of the information necessary to
            understand its allusions, are rendered
            sufficiently obvious to the reader.</p>
            <p>Second paragraph here.</p>
            </body></html>
        """.trimIndent()

        val result = HtmlCleaner.toPlainText(html)

        assertEquals(
            "It is believed that the scene of this tale, and most of the information necessary to understand its allusions, are rendered sufficiently obvious to the reader.\n\nSecond paragraph here.",
            result,
        )
    }

    @Test
    fun `epub inline elements keep word boundaries`() {
        val html = "<p>a <b>bold</b> <i>word</i> here</p>"

        val result = HtmlCleaner.toPlainText(html)

        assertEquals("a bold word here", result)
    }

    @Test
    fun `epub block elements become paragraph breaks`() {
        val html = "<h1>INTRODUCTION</h1><p>Body text.</p>"

        val result = HtmlCleaner.toPlainText(html)

        assertEquals("INTRODUCTION\n\nBody text.", result)
    }

    @Test
    fun `epub output has no single newlines left for reflow to mishandle`() {
        val html = "<p>line one\nline two</p><p>para two</p>"

        val result = HtmlCleaner.toPlainText(html)

        assertFalse(result.contains(Regex("[^\n]\n[^\n]")))
    }

    @Test
    fun `epub cleaned text survives reflow normalization unchanged`() {
        val html = """
            <html><body>
            <h1>CHAPTER I</h1>
            <p>It is believed that the scene of this tale,
            and most of the information necessary to
            understand its allusions, are rendered
            sufficiently obvious to the reader.</p>
            <p>Second paragraph here.</p>
            </body></html>
        """.trimIndent()

        val cleaned = HtmlCleaner.toPlainText(html)

        assertEquals(cleaned, normalizeForReflow(cleaned))
    }

    // endregion
}
