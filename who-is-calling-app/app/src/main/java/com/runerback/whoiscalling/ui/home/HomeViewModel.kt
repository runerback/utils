package com.runerback.whoiscalling.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.whoiscalling.data.settings.LookupSettings
import com.runerback.whoiscalling.data.settings.SettingsRepository
import com.runerback.whoiscalling.service.RecentLookupsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    recentLookupsStore: RecentLookupsStore,
) : ViewModel() {

    val settings: StateFlow<LookupSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LookupSettings())

    val recentLookups = recentLookupsStore.lookups

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEnabled(enabled) }
    }
}
