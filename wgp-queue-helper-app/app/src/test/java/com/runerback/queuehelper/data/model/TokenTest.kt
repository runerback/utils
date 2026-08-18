package com.runerback.queuehelper.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenTest {

    @Test
    fun `parse empty string returns empty list`() {
        assertEquals(emptyList<Token>(), "".parseTokens())
    }

    @Test
    fun `parse plain text returns single PlainText token`() {
        assertEquals(listOf(Token.PlainText("hello world")), "hello world".parseTokens())
    }

    @Test
    fun `parse picture token`() {
        assertEquals(listOf(Token.Picture(1)), "<Picture 1>".parseTokens())
    }

    @Test
    fun `parse subject token`() {
        assertEquals(listOf(Token.Subject(2)), "<Subject 2>".parseTokens())
    }

    @Test
    fun `parse audio token`() {
        assertEquals(listOf(Token.Audio(1)), "<Audio 1>".parseTokens())
    }

    @Test
    fun `parse mixed text and tokens`() {
        val text = "intro <Picture 1> middle <Subject 2> end"
        val expected = listOf(
            Token.PlainText("intro "),
            Token.Picture(1),
            Token.PlainText(" middle "),
            Token.Subject(2),
            Token.PlainText(" end")
        )
        assertEquals(expected, text.parseTokens())
    }

    @Test
    fun `parse tokens are case insensitive`() {
        assertEquals(listOf(Token.Picture(1)), "<picture 1>".parseTokens())
        assertEquals(listOf(Token.Subject(1)), "<subject 1>".parseTokens())
        assertEquals(listOf(Token.Audio(1)), "<audio 1>".parseTokens())
    }

    @Test
    fun `export empty list returns empty string`() {
        assertEquals("", emptyList<Token>().export())
    }

    @Test
    fun `export PlainText returns text as is`() {
        assertEquals("hello", listOf(Token.PlainText("hello")).export())
    }

    @Test
    fun `export Picture returns picture tag`() {
        assertEquals("<Picture 3>", listOf(Token.Picture(3)).export())
    }

    @Test
    fun `export Subject returns subject tag`() {
        assertEquals("<Subject 5>", listOf(Token.Subject(5)).export())
    }

    @Test
    fun `export Audio returns audio tag`() {
        assertEquals("<Audio 2>", listOf(Token.Audio(2)).export())
    }

    @Test
    fun `export mixed tokens returns original string`() {
        val tokens = listOf(
            Token.PlainText("intro "),
            Token.Picture(1),
            Token.PlainText(" middle "),
            Token.Subject(2),
            Token.PlainText(" end")
        )
        assertEquals("intro <Picture 1> middle <Subject 2> end", tokens.export())
    }

    @Test
    fun `parse export roundtrip preserves string`() {
        val text = "a <Picture 1> b <Subject 2> c <Audio 1> d"
        assertEquals(text, text.parseTokens().export())
    }

    @Test
    fun `remove token at index removes chip`() {
        val tokens = "a <Picture 1> b".parseTokens().toMutableList()
        tokens.removeAt(1)
        assertEquals("a  b", tokens.export())
    }

    @Test
    fun `merge adjacent plain text after removal`() {
        val tokens = "a <Picture 1> b".parseTokens().toMutableList()
        tokens.removeAt(1)
        val merged = tokens.mergeAdjacentPlainText()
        assertEquals(listOf(Token.PlainText("a  b")), merged)
    }

    @Test
    fun `insert token between plain text splits it`() {
        val tokens: MutableList<Token> = listOf(Token.PlainText("hello world")).toMutableList()
        tokens.splitAndInsert(0, 5, Token.Picture(1))
        assertEquals(
            listOf(
                Token.PlainText("hello"),
                Token.Picture(1),
                Token.PlainText(" world")
            ),
            tokens
        )
    }

    @Test
    fun `split and insert at cursor zero puts token at start`() {
        val tokens: MutableList<Token> = listOf(Token.PlainText("abc")).toMutableList()
        tokens.splitAndInsert(0, 0, Token.Subject(1))
        assertEquals(
            listOf(Token.PlainText(""), Token.Subject(1), Token.PlainText("abc")),
            tokens
        )
    }

    @Test
    fun `split and insert at end puts token at end`() {
        val tokens: MutableList<Token> = listOf(Token.PlainText("abc")).toMutableList()
        tokens.splitAndInsert(0, 3, Token.Audio(1))
        assertEquals(
            listOf(Token.PlainText("abc"), Token.Audio(1), Token.PlainText("")),
            tokens
        )
    }

    @Test
    fun `replace typed token changes number`() {
        val tokens = "<Picture 1>".parseTokens().toMutableList()
        tokens[0] = Token.Picture(2)
        assertEquals("<Picture 2>", tokens.export())
    }
}
