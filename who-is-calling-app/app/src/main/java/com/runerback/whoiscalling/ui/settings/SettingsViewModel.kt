package com.runerback.whoiscalling.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.whoiscalling.data.api.LookupApi
import com.runerback.whoiscalling.data.api.LookupResult
import com.runerback.whoiscalling.data.settings.LookupSettings
import com.runerback.whoiscalling.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TestLookupState {
    data object Idle : TestLookupState
    data object Running : TestLookupState
    data class Success(val result: LookupResult) : TestLookupState
    data class Failure(val message: String) : TestLookupState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val lookupApi: LookupApi,
) : ViewModel() {

    val settings: StateFlow<LookupSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LookupSettings())

    private val _testState = MutableStateFlow<TestLookupState>(TestLookupState.Idle)
    val testState: StateFlow<TestLookupState> = _testState.asStateFlow()

    fun setApiUrlTemplate(template: String) {
        viewModelScope.launch { settingsRepository.setApiUrlTemplate(template) }
    }

    fun setTimeoutMillis(timeoutMillis: Long) {
        viewModelScope.launch { settingsRepository.setTimeoutMillis(timeoutMillis) }
    }

    fun setBlockSpam(blockSpam: Boolean) {
        viewModelScope.launch { settingsRepository.setBlockSpam(blockSpam) }
    }

    fun setNotifyOnLookupFailure(notify: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyOnLookupFailure(notify) }
    }

    fun testLookup(number: String) {
        val current = settings.value
        viewModelScope.launch {
            _testState.value = TestLookupState.Running
            _testState.value = lookupApi
                .lookup(current.apiUrlTemplate, number, current.timeoutMillis)
                .fold(
                    onSuccess = { TestLookupState.Success(it) },
                    onFailure = { TestLookupState.Failure(it.message ?: it.javaClass.simpleName) },
                )
        }
    }

    fun resetTestState() {
        _testState.value = TestLookupState.Idle
    }
}
