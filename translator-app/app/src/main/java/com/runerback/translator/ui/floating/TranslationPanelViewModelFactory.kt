package com.runerback.translator.ui.floating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.runerback.translator.data.SettingsRepository

class TranslationPanelViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TranslationPanelViewModel::class.java)) {
            return TranslationPanelViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
