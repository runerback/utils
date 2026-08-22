package com.runerback.translator.ui.floating

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.translator.argostranslate.ArgosTranslateClient
import com.runerback.translator.data.SettingsRepository
import com.runerback.translator.ollama.OllamaClient
import com.runerback.translator.translate.TranslationProvider
import com.runerback.translator.translate.TranslationResult
import com.runerback.translator.translate.Translator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TranslationPanelViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    var isVisible by mutableStateOf(false)
        private set

    var state by mutableStateOf<TranslationState>(TranslationState.Idle)
        private set

    var anchor by mutableStateOf(Rect())
        private set

    private var currentSourceText: String = ""

    fun show(text: String, anchorRect: Rect) {
        currentSourceText = text
        anchor = anchorRect
        isVisible = true
        state = TranslationState.Loading
        viewModelScope.launch {
            val provider = settingsRepository.translationProvider.first()
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
                state = TranslationState.Success(result.text)
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
        }
    }
}
