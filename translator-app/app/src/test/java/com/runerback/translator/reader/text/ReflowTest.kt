package com.runerback.translator.reader.text

import com.runerback.translator.reader.epub.HtmlCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReflowTest {

    @Test
    fun `hard-wrapped lines are joined into one paragraph`() {
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
    fun `blank lines separate paragraphs and are preserved`() {
        val extracted = """
            paragraph one line one
            paragraph one line two

            paragraph two line one
            paragraph two line two
        """.trimIndent()

        val result = normalizeForReflow(extracted)

        assertEquals(
            "paragraph one line one paragraph one line two\n\nparagraph two line one paragraph two line two",
            result,
        )
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
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals("a\n\nb", normalizeForReflow("  a  \n\n  b  "))
    }

    @Test
    fun `normalization is idempotent`() {
        val once = normalizeForReflow("one\ntwo\n\nthree\nfour")
        assertEquals(once, normalizeForReflow(once))
    }

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
}
