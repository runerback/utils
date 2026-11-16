package com.runerback.translator.dictionary

import com.runerback.translator.translate.TranslationResult
import com.runerback.translator.translate.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.subread.dictionary.core.Lookup

/**
 * Local offline provider: looks the selection up in the bundled Yomitan dictionaries,
 * the longest prefix first with deinflection, exactly like the Yomitan popup.
 * All three `Translator` directions run the same lookup: the glossary language is
 * whatever the bundled dictionaries contain. `simplifyEnglish` falls back to the lookup;
 * the Simplify button is hidden for this provider anyway.
 */
class LocalDictionaryTranslator(private val dictionaries: DictionaryLookupSource) : Translator {

    override suspend fun translateToEnglish(text: String): TranslationResult = lookup(text)
    override suspend fun simplifyEnglish(text: String): TranslationResult = lookup(text)
    override suspend fun translateToChinese(text: String): TranslationResult = lookup(text)

    private suspend fun lookup(text: String): TranslationResult = withContext(Dispatchers.IO) {
        if (!dictionaries.anyEnabled()) throw DictionaryUnavailableException("No dictionary bundled in this build")
        val query = text.trim()
        val matches = Lookup.find(query, 0, dictionaries)
        if (matches.isEmpty()) {
            TranslationResult("No dictionary entry found for $query")
        } else {
            TranslationResult(DictionaryFormatter.format(matches) { expression -> dictionaries.meta(expression) })
        }
    }
}

class DictionaryUnavailableException(message: String) : Exception(message)
