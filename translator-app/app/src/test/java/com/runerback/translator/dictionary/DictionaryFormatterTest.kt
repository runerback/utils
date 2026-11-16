package com.runerback.translator.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import space.subread.dictionary.core.Lookup
import space.subread.dictionary.core.StoredTerm
import space.subread.dictionary.core.Term
import space.subread.dictionary.core.TermMeta

class DictionaryFormatterTest {

    private fun stored(id: Long, title: String, position: Int, expression: String, reading: String = "", glossary: String = "[\"definition\"]", rules: String = "") =
        StoredTerm(id, title, position, Term(expression, reading, "", rules, 0.0, glossary, 0L, ""))

    @Test
    fun `single hit shows expression reading and glossary`() {
        val matches = Lookup.find("食べる", 0, sourceOf(stored(1, "Jitendex", 1, "食べる", "たべる", "[\"to eat\"]")))
        assertEquals(1, matches.size)
        val text = DictionaryFormatter.format(matches) { emptyList() }
        assertEquals("食べる (たべる)\n  to eat", text)
    }

    @Test
    fun `deinflected hit shows the reasons`() {
        // 食べた is the past of 食べる: the match must carry the deinflection step.
        val matches = Lookup.find("食べた", 0, sourceOf(stored(1, "Jitendex", 1, "食べる", "たべる", "[\"to eat\"]", rules = "v1")))
        assertEquals(1, matches.size)
        val text = DictionaryFormatter.format(matches) { emptyList() }
        assertTrue(text, text.startsWith("食べる (たべる) ← "))
        assertTrue(text, text.contains("past"))
    }

    @Test
    fun `several dictionaries label each entry`() {
        val matches = Lookup.find("猫", 0, sourceOf(
            stored(1, "Jitendex", 1, "猫", "ねこ", "[\"cat\"]"),
            stored(2, "JMdict", 2, "猫", "ねこ", "[\"cat (esp. the domestic cat)\"]"),
        ))
        assertEquals(2, matches.size)
        val text = DictionaryFormatter.format(matches) { emptyList() }
        assertTrue(text, text.contains("[Jitendex]"))
        assertTrue(text, text.contains("[JMdict]"))
    }

    @Test
    fun `one dictionary omits the label`() {
        val matches = Lookup.find("猫", 0, sourceOf(stored(1, "Jitendex", 1, "猫", "ねこ", "[\"cat\"]")))
        val text = DictionaryFormatter.format(matches) { emptyList() }
        assertTrue(text, !text.contains("["))
    }

    @Test
    fun `pitch and frequency meta render as lines`() {
        val matches = Lookup.find("猫", 0, sourceOf(stored(1, "Jitendex", 1, "猫", "ねこ", "[\"cat\"]")))
        val text = DictionaryFormatter.format(matches) { expression ->
            assertEquals("猫", expression)
            listOf(
                StoredMeta("accent", TermMeta("猫", "pitch", "{\"reading\":\"ねこ\",\"pitches\":[{\"position\":0}]}")),
                StoredMeta("freq", TermMeta("猫", "freq", "{\"value\":1234}")),
            )
        }
        assertTrue(text, text.contains("pitch: ねこ [0]"))
        assertTrue(text, text.contains("freq: 1234"))
    }

    @Test
    fun `structured glossary renders as plain text lines`() {
        val glossary = """["to eat",{"type":"structured-content","content":{"tag":"ul","content":[{"tag":"li","content":"to consume"},{"tag":"li","content":"to live on"}]}}]"""
        val matches = Lookup.find("食べる", 0, sourceOf(stored(1, "Jitendex", 1, "食べる", "たべる", glossary)))
        val text = DictionaryFormatter.format(matches) { emptyList() }
        assertTrue(text, text.contains("  to eat"))
        assertTrue(text, text.contains("to consume"))
        assertTrue(text, !text.contains("<"))
    }

    private fun sourceOf(vararg terms: StoredTerm) = object : DictionaryLookupSource {
        override fun anyEnabled() = terms.isNotEmpty()
        override fun meta(expression: String) = emptyList<StoredMeta>()
        override fun terms(texts: Collection<String>) =
            terms.filter { it.term.expression in texts || it.term.reading in texts }
    }
}
