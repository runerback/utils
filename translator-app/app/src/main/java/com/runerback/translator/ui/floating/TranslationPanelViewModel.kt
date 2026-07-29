package com.runerback.translator.ui.floating

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.translator.data.SettingsRepository
import com.runerback.translator.ollama.OllamaClient
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
        translate { translateToEnglish(text) }
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

    private fun translate(block: suspend OllamaClient.() -> com.runerback.translator.data.OllamaResponse) {
        viewModelScope.launch {
            try {
                val baseUrl = settingsRepository.baseUrl.first()
                val model = settingsRepository.model.first()
                val temperature = settingsRepository.temperature.first()
                val client = OllamaClient(baseUrl)
                val response = block(client)
                state = TranslationState.Success(response.message.content)
            } catch (e: Exception) {
                state = TranslationState.Error(e.message ?: "Translation failed")
            }
        }
    }
}
