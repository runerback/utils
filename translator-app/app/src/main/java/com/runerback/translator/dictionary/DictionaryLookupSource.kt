package com.runerback.translator.dictionary

import space.subread.dictionary.core.TermSource

/** The lookup surface `LocalDictionaryTranslator` needs. `DictionaryStore` implements it; tests fake it. */
interface DictionaryLookupSource : TermSource {
    fun anyEnabled(): Boolean
    fun meta(expression: String): List<StoredMeta>
}
