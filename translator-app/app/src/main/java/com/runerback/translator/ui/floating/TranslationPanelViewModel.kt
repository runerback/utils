package com.runerback.translator.ui.floating

import android.content.Context
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.translator.argostranslate.ArgosTranslateClient
import com.runerback.translator.data.SettingsRepository
import com.runerback.translator.dictionary.DictionaryFormatter
import com.runerback.translator.dictionary.DictionaryStoreProvider
import com.runerback.translator.dictionary.DictionaryUnavailableException
import com.runerback.translator.dictionary.LocalDictionaryTranslator
import com.runerback.translator.ollama.OllamaClient
import com.runerback.translator.translate.TranslationProvider
import com.runerback.translator.translate.TranslationResult
import com.runerback.translator.translate.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.subread.dictionary.core.Lookup
import java.text.BreakIterator

class TranslationPanelViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    enum class Source { TEXT_SELECTION, OCR }

    var isVisible by mutableStateOf(false)
        private set

    var state by mutableStateOf<TranslationState>(TranslationState.Idle)
        private set

    var anchor by mutableStateOf(Rect())
        private set

    /** Whole-text action buttons (Simplify/Chinese). Hidden for the dictionary
     *  provider, where whole-sentence lookups only produce noise. */
    var showActionButtons by mutableStateOf(true)
        private set

    private var currentSourceText: String = ""
    private var currentSource: Source = Source.TEXT_SELECTION
    private var currentProvider: TranslationProvider = TranslationProvider.OLLAMA

    fun show(text: String, anchorRect: Rect, source: Source = Source.TEXT_SELECTION) {
        currentSourceText = text
        currentSource = source
        anchor = anchorRect
        isVisible = true
        state = TranslationState.Loading
        viewModelScope.launch {
            val provider = settingsRepository.translationProvider.first()
            currentProvider = provider
            showActionButtons = provider != TranslationProvider.DICTIONARY
            if (provider == TranslationProvider.DICTIONARY) {
                // The dictionary engine scans at a position inside a text (the
                // Yomitan model), so the user picks the word. Selections are
                // already word-ish: look them up right away; OCR lines wait
                // for a tap instead of failing on the whole sentence.
                state = TranslationState.PickWord(sourceText = text)
                if (source == Source.TEXT_SELECTION) {
                    lookupAt(0)
                }
                return@launch
            }
            state = TranslationState.Loading
            val targetLanguage = if (provider == TranslationProvider.ARGOSTRANSLATE) {
                settingsRepository.targetLanguage.first()
            } else {
                "en"
            }
            translate {
                when (targetLanguage) {
                    "zh" -> translateToChinese(text)
                    else -> translateToEnglish(text)
                }
            }
        }
    }

    /** Dictionary pick mode: scan from [offset] in the source text, exactly
     *  like Yomitan scanning at the cursor. No-op for other providers. */
    fun onWordTapped(offset: Int) {
        if (currentProvider != TranslationProvider.DICTIONARY) return
        lookupAt(offset)
    }

    private fun lookupAt(offset: Int) {
        val current = state as? TranslationState.PickWord ?: return
        if (current.sourceText.isBlank()) return
        val safeOffset = offset.coerceIn(0, current.sourceText.length - 1)
        // The tapped word's bounds: highlight on every outcome — hit, miss, or
        // error — so the tap always has visible feedback.
        val word = wordBoundsAround(current.sourceText, safeOffset)
        state = current.copy(loading = true, highlight = null)
        viewModelScope.launch {
            try {
                val store = DictionaryStoreProvider.get(context)
                val matches = withContext(Dispatchers.IO) {
                    if (!store.anyEnabled()) throw DictionaryUnavailableException("No dictionary imported — add one in Settings")
                    Lookup.find(current.sourceText, safeOffset, store)
                }
                state = if (matches.isEmpty()) {
                    current.copy(
                        loading = false,
                        result = PickResult.Message("No dictionary entry found for ${current.sourceText.substring(word)}"),
                        highlight = word,
                    )
                } else {
                    val entry = withContext(Dispatchers.IO) {
                        DictionaryFormatter.formatEntry(matches) { expression -> store.meta(expression) }
                    }
                    // Matches come longest first: highlight what the top match covers.
                    current.copy(
                        loading = false,
                        result = PickResult.Found(entry),
                        highlight = safeOffset until (safeOffset + matches.first().length),
                    )
                }
            } catch (e: Exception) {
                state = current.copy(
                    loading = false,
                    result = PickResult.Message(e.message ?: "Lookup failed"),
                    highlight = word,
                )
            }
        }
    }

    /** Bounds of the word around [offset] — used to highlight the tap and to
     *  name what failed in the no-entry message. */
    private fun wordBoundsAround(text: String, offset: Int): IntRange {
        val iterator = BreakIterator.getWordInstance()
        iterator.setText(text)
        var start = iterator.preceding(offset)
        if (start == BreakIterator.DONE) start = 0
        var end = iterator.following(offset)
        if (end == BreakIterator.DONE) end = text.length
        while (end > start && text[end - 1].isWhitespace()) end--
        while (start < end && text[start].isWhitespace()) start++
        return start until end
    }

    fun dismiss() {
        isVisible = false
        state = TranslationState.Idle
    }

    fun onSimplify() {
        if (currentSourceText.isBlank()) return
        state = TranslationState.Loading
        translate { simplifyEnglish(currentSourceText) }
    }

    fun onChinese() {
        if (currentSourceText.isBlank()) return
        state = TranslationState.Loading
        translate { translateToChinese(currentSourceText) }
    }

    private fun translate(block: suspend Translator.() -> TranslationResult) {
        viewModelScope.launch {
            try {
                val translator = createTranslator()
                val result = block(translator)
                val sourceText = if (currentSource == Source.OCR) currentSourceText else null
                state = TranslationState.Success(result.text, sourceText)
            } catch (e: Exception) {
                state = TranslationState.Error(e.message ?: "Translation failed")
            }
        }
    }

    private suspend fun createTranslator(): Translator {
        return when (settingsRepository.translationProvider.first()) {
            TranslationProvider.OLLAMA -> {
                val baseUrl = settingsRepository.baseUrl.first()
                val model = settingsRepository.model.first()
                val temperature = settingsRepository.temperature.first()
                OllamaClient(baseUrl, model, temperature)
            }
            TranslationProvider.ARGOSTRANSLATE -> {
                val baseUrl = settingsRepository.argosTranslateBaseUrl.first()
                val sourceLanguage = settingsRepository.sourceLanguage.first()
                ArgosTranslateClient(baseUrl, sourceLanguage)
            }
            TranslationProvider.DICTIONARY -> {
                LocalDictionaryTranslator(DictionaryStoreProvider.get(context))
            }
        }
    }
}
