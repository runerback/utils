package com.runerback.files.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.files.data.datasource.SettingsDataSource
import com.runerback.files.data.model.FileNode
import com.runerback.files.data.model.FileSource
import com.runerback.files.data.model.TabConfig
import com.runerback.files.data.repository.FileRepositoryFactory
import com.runerback.files.data.repository.SMBFileRepository
import com.runerback.files.data.settings.AppSettings
import com.runerback.files.ui.components.LogBuffer
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

    private val _trees = MutableStateFlow(mapOf<String, List<FileNode>>())
    val trees: StateFlow<Map<String, List<FileNode>>> = _trees.asStateFlow()

    private val _selectedNodes = MutableStateFlow(mapOf<String, FileNode?>())

    private val _multiSelectActive = MutableStateFlow(mapOf<String, Boolean>())
    val multiSelectActive: StateFlow<Map<String, Boolean>> = _multiSelectActive.asStateFlow()

    private val _selectedNodeIds = MutableStateFlow(mapOf<String, Set<String>>())
    val selectedNodeIds: StateFlow<Map<String, Set<String>>> = _selectedNodeIds.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _smbDialogState = MutableStateFlow<SmbDialogState>(SmbDialogState.Hidden)
    val smbDialogState: StateFlow<SmbDialogState> = _smbDialogState.asStateFlow()

    private val _settingsDialogVisible = MutableStateFlow(false)
    val settingsDialogVisible: StateFlow<Boolean> = _settingsDialogVisible.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                settingsDataSource.selectedTabIndex.collect { index ->
                    val safeIndex = index.coerceIn(0, (tabs.value.size - 1).coerceAtLeast(0))
                    if (safeIndex != _selectedTabIndex.value) {
                        _selectedTabIndex.value = safeIndex
                        ensureActiveTabLoaded()
                    }
                }
            } catch (e: Exception) {
                logError("FilesViewModel.init selectedTabIndex", e)
            }
        }
        viewModelScope.launch {
            try {
                settingsDataSource.tabs.collect { tabs ->
                    _tabs.value = tabs
                    if (_selectedTabIndex.value >= tabs.size) {
                        _selectedTabIndex.value = (tabs.size - 1).coerceAtLeast(0)
                    }
                    ensureActiveTabLoaded()
                }
            } catch (e: Exception) {
                logError("FilesViewModel.init", e)
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
            ensureActiveTabLoaded()
        } catch (e: Exception) {
            logError("FilesViewModel.selectTab", e)
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
            logError("FilesViewModel.addLocalTab", e)
        }
    }

    fun addSmbTab() {
        openAddSmbDialog()
    }

    fun openAddSmbDialog() {
        try {
            _smbDialogState.value = SmbDialogState.Add
        } catch (e: Exception) {
            logError("FilesViewModel.openAddSmbDialog", e)
        }
    }

    fun openEditSmbDialog(tabId: String) {
        try {
            val tab = tabs.value.find { it.id == tabId } ?: return
            val source = tab.source as? FileSource.Smb ?: return
            _smbDialogState.value = SmbDialogState.Edit(tabId, source)
        } catch (e: Exception) {
            logError("FilesViewModel.openEditSmbDialog", e)
        }
    }

    fun dismissSmbDialog() {
        try {
            _smbDialogState.value = SmbDialogState.Hidden
        } catch (e: Exception) {
            logError("FilesViewModel.dismissSmbDialog", e)
        }
    }

    fun openSettingsDialog() {
        try {
            _settingsDialogVisible.value = true
        } catch (e: Exception) {
            logError("FilesViewModel.openSettingsDialog", e)
        }
    }

    fun dismissSettingsDialog() {
        try {
            _settingsDialogVisible.value = false
        } catch (e: Exception) {
            logError("FilesViewModel.dismissSettingsDialog", e)
        }
    }

    fun saveSmbTimeoutMillis(timeoutMillis: Long) {
        try {
            AppSettings.saveSmbTimeoutMillis(timeoutMillis)
        } catch (e: Exception) {
            logError("FilesViewModel.saveSmbTimeoutMillis", e)
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
                        _trees.value = _trees.value - state.tabId
                        loadTree(state.tabId)
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
                        loadTree(newTab.id)
                    }
                    SmbDialogState.Hidden -> { /* no-op */ }
                }
                dismissSmbDialog()
            } catch (e: Exception) {
                logError("FilesViewModel.saveSmbServer", e)
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
            viewModelScope.launch {
                settingsDataSource.saveTabs(updatedTabs)
                settingsDataSource.saveSelectedTabIndex(newIndex)
                _trees.value = _trees.value - tab.id
                _selectedNodes.value = _selectedNodes.value - tab.id
                _multiSelectActive.value = _multiSelectActive.value - tab.id
                _selectedNodeIds.value = _selectedNodeIds.value - tab.id
            }
        } catch (e: Exception) {
            logError("FilesViewModel.removeTab", e)
        }
    }

    fun renameTab(index: Int, name: String) {
        try {
            val tab = tabs.value.getOrNull(index) ?: return
            viewModelScope.launch {
                settingsDataSource.renameTab(tab.id, name)
            }
        } catch (e: Exception) {
            logError("FilesViewModel.renameTab", e)
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
                loadTree(tabId)
            } catch (e: Exception) {
                logError("FilesViewModel.setLocalRoot", e)
            }
        }
    }

    fun toggleNode(tabId: String, node: FileNode) {
        if (!node.isDirectory) return
        viewModelScope.launch {
            try {
                val currentTree = _trees.value[tabId] ?: return@launch
                if (node.children == null) {
                    updateTree(tabId, currentTree, node.id) { it.copy(isLoading = true) }
                    loadChildren(tabId, node, _trees.value[tabId] ?: currentTree)
                } else {
                    updateTree(tabId, currentTree, node.id) { it.copy(isExpanded = !it.isExpanded) }
                }
            } catch (e: Exception) {
                logError("FilesViewModel.toggleNode", e)
            }
        }
    }

    fun selectNode(tabId: String, node: FileNode) {
        try {
            _selectedNodes.value = _selectedNodes.value + (tabId to node)
            if (node.isDirectory) {
                toggleNode(tabId, node)
            }
        } catch (e: Exception) {
            logError("FilesViewModel.selectNode", e)
        }
    }

    fun toggleMultiSelect(tabId: String) {
        try {
            val current = _multiSelectActive.value[tabId] ?: false
            _multiSelectActive.value = _multiSelectActive.value + (tabId to !current)
        } catch (e: Exception) {
            logError("FilesViewModel.toggleMultiSelect", e)
        }
    }

    fun toggleNodeSelection(tabId: String, node: FileNode) {
        try {
            if (node.isDirectory) return
            val current = _selectedNodeIds.value[tabId] ?: emptySet()
            val updated = if (current.contains(node.id)) current - node.id else current + node.id
            _selectedNodeIds.value = _selectedNodeIds.value + (tabId to updated)
        } catch (e: Exception) {
            logError("FilesViewModel.toggleNodeSelection", e)
        }
    }

    fun isNodeSelected(tabId: String, nodeId: String): Boolean {
        return _selectedNodeIds.value[tabId]?.contains(nodeId) ?: false
    }

    fun clearError() {
        _error.value = null
    }

    private fun ensureActiveTabLoaded() {
        try {
            val tab = tabs.value.getOrNull(selectedTabIndex.value) ?: return
            if (_trees.value.containsKey(tab.id)) return
            loadTree(tab.id)
        } catch (e: Exception) {
            logError("FilesViewModel.ensureActiveTabLoaded", e)
        }
    }

    private fun loadTree(tabId: String) {
        viewModelScope.launch {
            try {
                withTimeout(AppSettings.smbTimeoutMillis.value) {
                    val tab = tabs.value.find { it.id == tabId } ?: return@withTimeout
                    when (val source = tab.source) {
                        is FileSource.Local -> {
                            if (source.rootUri.toString().isEmpty()) return@withTimeout
                            val repository = repositoryFactory.create(source)
                            repository.loadRoot().onSuccess { root ->
                                if (root.isDirectory) {
                                    repository.listChildren(root.id).onSuccess { children ->
                                        _trees.value = _trees.value + (tabId to children)
                                    }.onFailure { e ->
                                        _trees.value = _trees.value + (tabId to listOf(root))
                                        LogBuffer.add("FilesViewModel.loadTree children: ${e.message}")
                                        _error.value = "Failed to load root children: ${e.message}"
                                    }
                                } else {
                                    _trees.value = _trees.value + (tabId to listOf(root))
                                }
                            }.onFailure { e ->
                                _trees.value = _trees.value + (tabId to emptyList())
                                LogBuffer.add("FilesViewModel.loadTree: ${e.message}")
                                _error.value = "Failed to load local root: ${e.message}"
                            }
                        }
                        is FileSource.Smb -> {
                            val repository = repositoryFactory.create(source)
                            repository.loadRoot().onSuccess { root ->
                                if (root.isDirectory) {
                                    repository.listChildren(root.id).onSuccess { children ->
                                        _trees.value = _trees.value + (tabId to children)
                                    }.onFailure { e ->
                                        _trees.value = _trees.value + (tabId to listOf(root))
                                        LogBuffer.add("FilesViewModel.loadTree SMB children: ${e.message}")
                                        _error.value = "Failed to load SMB root children: ${e.message}"
                                    }
                                } else {
                                    _trees.value = _trees.value + (tabId to listOf(root))
                                }
                            }.onFailure { e ->
                                _trees.value = _trees.value + (tabId to emptyList())
                                LogBuffer.add("FilesViewModel.loadTree SMB: ${e.message}")
                                _error.value = "Failed to load SMB root: ${e.message}"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _trees.value = _trees.value + (tabId to emptyList())
                logError("FilesViewModel.loadTree", e)
            }
        }
    }

    private suspend fun loadChildren(tabId: String, parent: FileNode, currentTree: List<FileNode>) {
        try {
            withTimeout(AppSettings.smbTimeoutMillis.value) {
                val tab = tabs.value.find { it.id == tabId } ?: return@withTimeout
                val repository = repositoryFactory.create(tab.source)
                repository.listChildren(parent.id).onSuccess { children ->
                    updateTree(tabId, currentTree, parent.id) {
                        it.copy(isLoading = false, isExpanded = true, children = children)
                    }
                }.onFailure { e ->
                    updateTree(tabId, currentTree, parent.id) { it.copy(isLoading = false) }
                    LogBuffer.add("FilesViewModel.loadChildren: ${e.message}")
                    _error.value = "Failed to load children: ${e.message}"
                }
            }
        } catch (e: Exception) {
            updateTree(tabId, _trees.value[tabId] ?: currentTree, parent.id) { it.copy(isLoading = false) }
            logError("FilesViewModel.loadChildren", e)
        }
    }

    private fun logError(tag: String, e: Throwable) {
        val message = "$tag: ${e.message}"
        LogBuffer.add("$tag: ${e.stackTraceToString()}")
        _error.value = message
    }

    private fun updateTree(
        tabId: String,
        currentTree: List<FileNode>,
        nodeId: String,
        transform: (FileNode) -> FileNode
    ) {
        try {
            val updatedTree = updateNode(currentTree, nodeId, transform)
            _trees.value = _trees.value + (tabId to updatedTree)
        } catch (e: Exception) {
            logError("FilesViewModel.updateTree", e)
        }
    }

    private fun updateNode(
        nodes: List<FileNode>,
        nodeId: String,
        transform: (FileNode) -> FileNode
    ): List<FileNode> {
        return nodes.map { node ->
            when {
                node.id == nodeId -> transform(node)
                node.children != null -> node.copy(children = updateNode(node.children, nodeId, transform))
                else -> node
            }
        }
    }
}

sealed class SmbDialogState {
    data object Hidden : SmbDialogState()
    data object Add : SmbDialogState()
    data class Edit(val tabId: String, val config: FileSource.Smb) : SmbDialogState()
}
