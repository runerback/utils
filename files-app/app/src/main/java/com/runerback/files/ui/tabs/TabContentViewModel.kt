package com.runerback.files.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.files.data.model.FileNode
import com.runerback.files.data.repository.FileRepository
import com.runerback.files.data.settings.AppSettings
import com.runerback.files.ui.components.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

abstract class TabContentViewModel(
    protected val repository: FileRepository,
    initialLoading: Boolean = false,
    ready: Boolean = true,
) : ViewModel() {

    protected open val isReady: Boolean = ready

    private val _tree = MutableStateFlow<List<FileNode>>(emptyList())
    val tree: StateFlow<List<FileNode>> = _tree.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _multiSelectActive = MutableStateFlow(false)
    val multiSelectActive: StateFlow<Boolean> = _multiSelectActive.asStateFlow()

    private val _selectedNodeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNodeIds: StateFlow<Set<String>> = _selectedNodeIds.asStateFlow()

    init {
        _isLoading.value = initialLoading
        if (initialLoading) {
            loadRoot()
        }
    }

    fun loadRoot() {
        if (!isReady) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(AppSettings.smbTimeoutMillis.value) {
                    repository.loadRoot().onSuccess { root ->
                        if (root.isDirectory) {
                            repository.listChildren(root.id).onSuccess { children ->
                                _tree.value = children
                            }.onFailure { e ->
                                _tree.value = listOf(root)
                                _error.value = "Failed to load root children: ${e.message}"
                            }
                        } else {
                            _tree.value = listOf(root)
                        }
                    }.onFailure { e ->
                        _tree.value = emptyList()
                        _error.value = "Failed to load root: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                _tree.value = emptyList()
                _error.value = "Failed to load root: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectNode(node: FileNode) {
        if (node.isDirectory) {
            toggleNode(node)
        }
    }

    fun toggleNode(node: FileNode) {
        if (!node.isDirectory) return
        viewModelScope.launch {
            val currentTree = _tree.value
            if (node.children == null) {
                _tree.value = updateNode(currentTree, node.id) { it.copy(isLoading = true) }
                loadChildren(node, _tree.value)
            } else {
                _tree.value = updateNode(currentTree, node.id) { it.copy(isExpanded = !it.isExpanded) }
            }
        }
    }

    private fun loadChildren(parent: FileNode, currentTree: List<FileNode>) {
        viewModelScope.launch {
            try {
                withTimeout(AppSettings.smbTimeoutMillis.value) {
                    repository.listChildren(parent.id).onSuccess { children ->
                        _tree.value = updateNode(currentTree, parent.id) {
                            it.copy(isLoading = false, isExpanded = true, children = children)
                        }
                    }.onFailure { e ->
                        _tree.value = updateNode(currentTree, parent.id) { it.copy(isLoading = false) }
                        LogBuffer.add("TabContentViewModel.loadChildren: ${e.message}")
                        _error.value = "Failed to load children: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                _tree.value = updateNode(currentTree, parent.id) { it.copy(isLoading = false) }
                LogBuffer.add("TabContentViewModel.loadChildren: ${e.message}")
                _error.value = "Failed to load children: ${e.message}"
            }
        }
    }

    fun toggleMultiSelect() {
        _multiSelectActive.value = !_multiSelectActive.value
    }

    fun toggleNodeSelection(node: FileNode) {
        if (node.isDirectory) return
        val current = _selectedNodeIds.value
        _selectedNodeIds.value = if (current.contains(node.id)) {
            current - node.id
        } else {
            current + node.id
        }
    }

    fun isNodeSelected(nodeId: String): Boolean {
        return _selectedNodeIds.value.contains(nodeId)
    }

    fun clearError() {
        _error.value = null
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
