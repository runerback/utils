package com.runerback.files.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.runerback.files.data.datasource.SettingsDataSource
import com.runerback.files.data.model.FileSource
import com.runerback.files.data.model.TabConfig
import com.runerback.files.data.repository.FileRepositoryFactory
import com.runerback.files.data.repository.SMBFileRepository
import com.runerback.files.data.settings.AppSettings
import com.runerback.files.ui.components.LogBuffer
import com.runerback.files.ui.tabs.LocalTabContentViewModel
import com.runerback.files.ui.tabs.SmbTabContentViewModel
import com.runerback.files.ui.tabs.TabContentViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val settingsDataSource: SettingsDataSource,
    private val repositoryFactory: FileRepositoryFactory
) : ViewModel() {

    private val _tabs = MutableStateFlow(
        listOf(
            TabConfig(
                id = UUID.randomUUID().toString(),
                name = "Local",
                source = FileSource.Local(Uri.EMPTY)
            )
        )
    )
    val tabs: StateFlow<List<TabConfig>> = _tabs.asStateFlow()

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _smbDialogState = MutableStateFlow<SmbDialogState>(SmbDialogState.Hidden)
    val smbDialogState: StateFlow<SmbDialogState> = _smbDialogState.asStateFlow()

    private val _settingsDialogVisible = MutableStateFlow(false)
    val settingsDialogVisible: StateFlow<Boolean> = _settingsDialogVisible.asStateFlow()

    private val tabStores = mutableMapOf<String, ViewModelStore>()

    init {
        viewModelScope.launch {
            try {
                settingsDataSource.selectedTabIndex.collect { index ->
                    val safeIndex = index.coerceIn(0, (tabs.value.size - 1).coerceAtLeast(0))
                    if (safeIndex != _selectedTabIndex.value) {
                        _selectedTabIndex.value = safeIndex
                    }
                }
            } catch (e: Exception) {
                LogBuffer.add("FilesViewModel.init selectedTabIndex: ${e.stackTraceToString()}")
            }
        }
        viewModelScope.launch {
            try {
                settingsDataSource.tabs.collect { tabs ->
                    _tabs.value = tabs
                    if (_selectedTabIndex.value >= tabs.size) {
                        _selectedTabIndex.value = (tabs.size - 1).coerceAtLeast(0)
                    }
                }
            } catch (e: Exception) {
                LogBuffer.add("FilesViewModel.init: ${e.stackTraceToString()}")
            }
        }
    }

    fun selectTab(index: Int) {
        try {
            if (index == _selectedTabIndex.value) return
            _selectedTabIndex.value = index
            viewModelScope.launch {
                settingsDataSource.saveSelectedTabIndex(index)
            }
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.selectTab: ${e.stackTraceToString()}")
        }
    }

    fun addLocalTab() {
        try {
            val newTab = TabConfig(
                id = UUID.randomUUID().toString(),
                name = "Local ${tabs.value.count { it.source is FileSource.Local } + 1}",
                source = FileSource.Local(Uri.EMPTY)
            )
            val updatedTabs = tabs.value + newTab
            _tabs.value = updatedTabs
            _selectedTabIndex.value = updatedTabs.lastIndex
            viewModelScope.launch {
                settingsDataSource.saveTabs(updatedTabs)
                settingsDataSource.saveSelectedTabIndex(updatedTabs.lastIndex)
            }
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.addLocalTab: ${e.stackTraceToString()}")
        }
    }

    fun addSmbTab() {
        openAddSmbDialog()
    }

    fun openAddSmbDialog() {
        try {
            _smbDialogState.value = SmbDialogState.Add
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.openAddSmbDialog: ${e.stackTraceToString()}")
        }
    }

    fun openEditSmbDialog(tabId: String) {
        try {
            val tab = tabs.value.find { it.id == tabId } ?: return
            val source = tab.source as? FileSource.Smb ?: return
            _smbDialogState.value = SmbDialogState.Edit(tabId, source)
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.openEditSmbDialog: ${e.stackTraceToString()}")
        }
    }

    fun dismissSmbDialog() {
        try {
            _smbDialogState.value = SmbDialogState.Hidden
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.dismissSmbDialog: ${e.stackTraceToString()}")
        }
    }

    fun openSettingsDialog() {
        try {
            _settingsDialogVisible.value = true
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.openSettingsDialog: ${e.stackTraceToString()}")
        }
    }

    fun dismissSettingsDialog() {
        try {
            _settingsDialogVisible.value = false
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.dismissSettingsDialog: ${e.stackTraceToString()}")
        }
    }

    fun saveSmbTimeoutMillis(timeoutMillis: Long) {
        try {
            AppSettings.saveSmbTimeoutMillis(timeoutMillis)
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.saveSmbTimeoutMillis: ${e.stackTraceToString()}")
        }
    }

    fun saveSmbServer(config: FileSource.Smb) {
        viewModelScope.launch {
            try {
                when (val state = _smbDialogState.value) {
                    is SmbDialogState.Edit -> {
                        val updatedTabs = tabs.value.map { tab ->
                            if (tab.id == state.tabId) {
                                tab.copy(name = config.name.ifEmpty { tab.name }, source = config)
                            } else {
                                tab
                            }
                        }
                        settingsDataSource.saveTabs(updatedTabs)
                        clearTabStore(state.tabId)
                    }
                    SmbDialogState.Add -> {
                        val newTab = TabConfig(
                            id = UUID.randomUUID().toString(),
                            name = config.name.ifEmpty { "SMB ${tabs.value.count { it.source is FileSource.Smb } + 1}" },
                            source = config
                        )
                        val updatedTabs = tabs.value + newTab
                        _tabs.value = updatedTabs
                        _selectedTabIndex.value = updatedTabs.lastIndex
                        settingsDataSource.saveTabs(updatedTabs)
                        settingsDataSource.saveSelectedTabIndex(updatedTabs.lastIndex)
                    }
                    SmbDialogState.Hidden -> { /* no-op */ }
                }
                dismissSmbDialog()
            } catch (e: Exception) {
                LogBuffer.add("FilesViewModel.saveSmbServer: ${e.stackTraceToString()}")
            }
        }
    }

    suspend fun testSmbConnection(config: FileSource.Smb): Result<String> {
        return try {
            withTimeout(10_000) {
                val repository = SMBFileRepository(config)
                repository.loadRoot().map { it.name }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun removeTab(index: Int) {
        try {
            val tab = tabs.value.getOrNull(index) ?: return
            val updatedTabs = tabs.value.filter { it.id != tab.id }
            _tabs.value = updatedTabs
            val newIndex = if (_selectedTabIndex.value >= updatedTabs.size) {
                updatedTabs.lastIndex.coerceAtLeast(0)
            } else {
                _selectedTabIndex.value
            }
            _selectedTabIndex.value = newIndex
            clearTabStore(tab.id)
            viewModelScope.launch {
                settingsDataSource.saveTabs(updatedTabs)
                settingsDataSource.saveSelectedTabIndex(newIndex)
            }
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.removeTab: ${e.stackTraceToString()}")
        }
    }

    fun renameTab(index: Int, name: String) {
        try {
            val tab = tabs.value.getOrNull(index) ?: return
            viewModelScope.launch {
                settingsDataSource.renameTab(tab.id, name)
            }
        } catch (e: Exception) {
            LogBuffer.add("FilesViewModel.renameTab: ${e.stackTraceToString()}")
        }
    }

    fun setLocalRoot(tabId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val savedTabs = settingsDataSource.tabs.first()
                val updatedTabs = savedTabs.map { tab ->
                    if (tab.id == tabId) tab.copy(source = FileSource.Local(uri)) else tab
                }
                settingsDataSource.saveTabs(updatedTabs)
                _tabs.value = updatedTabs
                clearTabStore(tabId)
            } catch (e: Exception) {
                LogBuffer.add("FilesViewModel.setLocalRoot: ${e.stackTraceToString()}")
            }
        }
    }

    fun getTabViewModel(tabId: String): TabContentViewModel {
        val store = tabStores.getOrPut(tabId) { ViewModelStore() }
        val factory = TabContentViewModelFactory(tabId, tabs.value, repositoryFactory)
        return ViewModelProvider(store, factory)[TabContentViewModel::class.java]
    }

    private fun clearTabStore(tabId: String) {
        tabStores.remove(tabId)?.clear()
    }

    override fun onCleared() {
        tabStores.values.forEach { it.clear() }
        tabStores.clear()
        super.onCleared()
    }

    private class TabContentViewModelFactory(
        private val tabId: String,
        private val tabs: List<TabConfig>,
        private val repositoryFactory: FileRepositoryFactory
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val tab = tabs.find { it.id == tabId }
                ?: throw IllegalArgumentException("Tab not found: $tabId")
            @Suppress("UNCHECKED_CAST")
            return when (val source = tab.source) {
                is FileSource.Local -> LocalTabContentViewModel(source, repositoryFactory)
                is FileSource.Smb -> SmbTabContentViewModel(source, repositoryFactory)
            } as T
        }
    }
}

sealed class SmbDialogState {
    data object Hidden : SmbDialogState()
    data object Add : SmbDialogState()
    data class Edit(val tabId: String, val config: FileSource.Smb) : SmbDialogState()
}
