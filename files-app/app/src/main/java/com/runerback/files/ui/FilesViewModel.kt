package com.runerback.files.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.files.data.datasource.SettingsDataSource
import com.runerback.files.data.model.FileNode
import com.runerback.files.data.model.FileSource
import com.runerback.files.data.model.TabConfig
import com.runerback.files.data.repository.FileRepositoryFactory
import com.runerback.files.ui.components.LogBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
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
            viewModelScope.launch {
                settingsDataSource.addTab(newTab)
                _selectedTabIndex.value = tabs.value.size
            }
        } catch (e: Exception) {
            logError("FilesViewModel.addLocalTab", e)
        }
    }

    fun addSmbTab() {
        try {
            val newTab = TabConfig(
                id = UUID.randomUUID().toString(),
                name = "SMB ${tabs.value.count { it.source is FileSource.Smb } + 1}",
                source = FileSource.Smb(
                    host = "",
                    share = "",
                    username = "",
                    password = ""
                )
            )
            viewModelScope.launch {
                settingsDataSource.addTab(newTab)
                _selectedTabIndex.value = tabs.value.size
            }
        } catch (e: Exception) {
            logError("FilesViewModel.addSmbTab", e)
        }
    }

    fun removeTab(index: Int) {
        try {
            val tab = tabs.value.getOrNull(index) ?: return
            viewModelScope.launch {
                settingsDataSource.removeTab(tab.id)
                _trees.value = _trees.value - tab.id
                _selectedNodes.value = _selectedNodes.value - tab.id
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
                val updatedTabs = tabs.value.map { tab ->
                    if (tab.id == tabId) tab.copy(source = FileSource.Local(uri)) else tab
                }
                settingsDataSource.saveTabs(updatedTabs)
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
                    loadChildren(tabId, node, currentTree)
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
                val tab = tabs.value.find { it.id == tabId } ?: return@launch
                when (val source = tab.source) {
                    is FileSource.Local -> {
                        if (source.rootUri.toString().isEmpty()) return@launch
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
                            LogBuffer.add("FilesViewModel.loadTree: ${e.message}")
                            _error.value = "Failed to load local root: ${e.message}"
                        }
                    }
                    is FileSource.Smb -> {
                        val repository = repositoryFactory.create(source)
                        repository.loadRoot().onSuccess { root ->
                            _trees.value = _trees.value + (tabId to listOf(root))
                        }.onFailure { e ->
                            LogBuffer.add("FilesViewModel.loadTree SMB: ${e.message}")
                            _error.value = "Failed to load SMB root: ${e.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                logError("FilesViewModel.loadTree", e)
            }
        }
    }

    private suspend fun loadChildren(tabId: String, parent: FileNode, currentTree: List<FileNode>) {
        try {
            val tab = tabs.value.find { it.id == tabId } ?: return
            val repository = repositoryFactory.create(tab.source)
            repository.listChildren(parent.id).onSuccess { children ->
                updateTree(tabId, currentTree, parent.id) {
                    it.copy(isExpanded = true, children = children)
                }
            }.onFailure { e ->
                LogBuffer.add("FilesViewModel.loadChildren: ${e.message}")
                _error.value = "Failed to load children: ${e.message}"
            }
        } catch (e: Exception) {
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
