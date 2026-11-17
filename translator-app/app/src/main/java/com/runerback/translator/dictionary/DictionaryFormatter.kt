package com.runerback.translator.dictionary

import space.subread.dictionary.core.Block
import space.subread.dictionary.core.Glossary
import space.subread.dictionary.core.Match
import space.subread.dictionary.core.Structure

/** One dictionary entry as the pick panel shows it: header fields, meta lines,
 *  and the glossary's structured content. */
data class DictEntry(
    val expression: String,
    val reading: String?,
    val reasons: String?,
    val metaLines: List<String>,
    val blocks: List<Block>,
)

/**
 * Turns lookup matches into the plain text the translation panel shows.
 * Kept separate from the translator so it can be unit-tested without Android.
 */
object DictionaryFormatter {

    /** More matches than this are noise: the popup-style list is longest-first, so the tail is rare short terms. */
    const val MAX_MATCHES = 20

    fun format(matches: List<Match>, meta: (String) -> List<StoredMeta>): String {
        val shown = matches.take(MAX_MATCHES)
        val severalDictionaries = shown.map { it.stored.dictionaryId }.distinct().size > 1
        return shown.joinToString("\n\n") { match ->
            formatMatch(match, meta(match.stored.term.expression), severalDictionaries)
        }
    }

    /** The pick panel shows only the best match, as a structured entry the panel
     *  renders natively (bullets, ruby, tag chips) instead of flattening to text. */
    fun formatEntry(matches: List<Match>, meta: (String) -> List<StoredMeta>): DictEntry {
        val match = matches.first()
        val term = match.stored.term
        return DictEntry(
            expression = term.expression,
            reading = term.reading.takeIf { it.isNotEmpty() && it != term.expression },
            reasons = match.reasons.joinToString(", ").takeIf { it.isNotEmpty() },
            metaLines = meta(term.expression).mapNotNull { stored ->
                val text = when (stored.meta.mode) {
                    "pitch" -> Glossary.pitchText(stored.meta.data)
                    "freq" -> Glossary.frequencyText(stored.meta.data)
                    else -> ""
                }
                if (text.isEmpty()) null else "${stored.meta.mode}: $text"
            },
            blocks = Structure.parse(term.glossary),
        )
    }

    private fun formatMatch(match: Match, meta: List<StoredMeta>, severalDictionaries: Boolean): String {
        val term = match.stored.term
        val header = buildString {
            append(term.expression)
            if (term.reading.isNotEmpty() && term.reading != term.expression) append(" (").append(term.reading).append(')')
            // The deinflection steps from the text to the dictionary form, e.g. "past, causative".
            if (match.reasons.isNotEmpty()) append(" ← ").append(match.reasons.joinToString(", "))
            if (severalDictionaries) append(" [").append(match.stored.dictionaryTitle).append(']')
        }
        val metaLines = meta.mapNotNull { stored ->
            val text = when (stored.meta.mode) {
                "pitch" -> Glossary.pitchText(stored.meta.data)
                "freq" -> Glossary.frequencyText(stored.meta.data)
                else -> ""
            }
            if (text.isEmpty()) null else "${stored.meta.mode}: $text"
        }
        val glossary = Glossary.toText(term.glossary)
        return buildString {
            append(header)
            for (line in metaLines) append('\n').append(line)
            if (glossary.isNotEmpty()) glossary.lines().forEach { append('\n').append("  ").append(it) }
        }
    }
}
