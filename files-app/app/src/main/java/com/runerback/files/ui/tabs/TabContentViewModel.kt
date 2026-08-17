package com.runerback.files.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runerback.files.data.model.FileNode
import com.runerback.files.data.repository.FileRepository
import com.runerback.files.data.settings.AppSettings
import com.runerback.files.ui.components.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed class DeleteTarget {
    data class Folder(val node: FileNode) : DeleteTarget()
    data class Files(val count: Int) : DeleteTarget()
}

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

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _rootId = MutableStateFlow<String?>(null)

    private val _deleteTarget = MutableStateFlow<DeleteTarget?>(null)
    val deleteTarget: StateFlow<DeleteTarget?> = _deleteTarget.asStateFlow()

    private val _deleteDialogVisible = MutableStateFlow(false)
    val deleteDialogVisible: StateFlow<Boolean> = _deleteDialogVisible.asStateFlow()

    val canDelete: StateFlow<Boolean> = combine(
        _multiSelectActive,
        _selectedNodeIds,
        _currentFolderId,
        _rootId,
    ) { multiSelect, selectedIds, currentFolderId, rootId ->
        val hasSelectedFiles = multiSelect && selectedIds.isNotEmpty()
        val hasDeletableFolder = currentFolderId != null && currentFolderId != rootId
        hasSelectedFiles || hasDeletableFolder
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _newTextDialogVisible = MutableStateFlow(false)
    val newTextDialogVisible: StateFlow<Boolean> = _newTextDialogVisible.asStateFlow()

    private val _newFolderDialogVisible = MutableStateFlow(false)
    val newFolderDialogVisible: StateFlow<Boolean> = _newFolderDialogVisible.asStateFlow()

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
                        _rootId.value = root.id
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
            _currentFolderId.value = node.id
            toggleNode(node)
        }
    }

    fun toggleNode(node: FileNode) {
        if (!node.isDirectory) return
        _currentFolderId.value = node.id
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

    fun resetCurrentFolder() {
        _currentFolderId.value = null
    }

    fun openNewTextDialog() {
        _newTextDialogVisible.value = true
    }

    fun dismissNewTextDialog() {
        _newTextDialogVisible.value = false
    }

    fun openNewFolderDialog() {
        _newFolderDialogVisible.value = true
    }

    fun dismissNewFolderDialog() {
        _newFolderDialogVisible.value = false
    }

    fun openDeleteDialog() {
        val selectedCount = if (_multiSelectActive.value) _selectedNodeIds.value.size else 0
        val target = if (selectedCount > 0) {
            DeleteTarget.Files(selectedCount)
        } else {
            _currentFolderId.value?.takeIf { it != _rootId.value }?.let { folderId ->
                findNode(_tree.value, folderId)?.let { DeleteTarget.Folder(it) }
            }
        }
        if (target != null) {
            _deleteTarget.value = target
            _deleteDialogVisible.value = true
        }
    }

    fun dismissDeleteDialog() {
        _deleteDialogVisible.value = false
        _deleteTarget.value = null
    }

    fun refreshActiveFolder() {
        val parentId = _currentFolderId.value ?: _rootId.value ?: return
        if (_isLoading.value) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                withTimeout(AppSettings.smbTimeoutMillis.value) {
                    refreshCurrentFolder(parentId)
                }
            } catch (e: Exception) {
                LogBuffer.add("TabContentViewModel.refreshActiveFolder: ${e.message}")
                _error.value = "Failed to refresh: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun confirmDelete() {
        val target = _deleteTarget.value ?: return
        _deleteDialogVisible.value = false
        _deleteTarget.value = null

        viewModelScope.launch {
            try {
                withTimeout(AppSettings.smbTimeoutMillis.value) {
                    when (target) {
                        is DeleteTarget.Folder -> deleteFolder(target.node)
                        is DeleteTarget.Files -> deleteFiles(_selectedNodeIds.value)
                    }
                }
            } catch (e: Exception) {
                LogBuffer.add("TabContentViewModel.confirmDelete: ${e.message}")
                _error.value = "Failed to delete: ${e.message}"
            }
        }
    }

    private suspend fun deleteFolder(node: FileNode) {
        _isLoading.value = true
        try {
            repository.delete(node.id).onSuccess {
                val parentId = findParentId(_tree.value, node.id) ?: _rootId.value
                _tree.value = removeNode(_tree.value, node.id)
                _currentFolderId.value = if (parentId == _rootId.value) null else parentId
                if (parentId != null) {
                    refreshCurrentFolder(parentId)
                } else {
                    loadRoot()
                }
            }.onFailure { e ->
                LogBuffer.add("TabContentViewModel.deleteFolder: ${e.message}")
                _error.value = "Failed to delete folder: ${e.message}"
            }
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun deleteFiles(ids: Set<String>) {
        _isLoading.value = true
        try {
            val remaining = ids.toMutableSet()
            val errors = mutableListOf<String>()
            ids.forEach { id ->
                repository.delete(id).onSuccess {
                    remaining.remove(id)
                }.onFailure { e ->
                    errors.add(e.message ?: "Unknown error")
                }
            }
            var currentTree = _tree.value
            ids.subtract(remaining).forEach { deletedId ->
                currentTree = removeNode(currentTree, deletedId)
            }
            _tree.value = currentTree
            _selectedNodeIds.value = remaining
            val parentId = _currentFolderId.value ?: _rootId.value
            if (parentId != null) {
                refreshCurrentFolder(parentId)
            }
            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} file(s): ${errors.first()}"
            } else {
                _multiSelectActive.value = false
                _selectedNodeIds.value = emptySet()
            }
        } finally {
            _isLoading.value = false
        }
    }

    fun createTextFile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == ".txt") {
            _error.value = "Please enter a file name"
            return
        }
        val parentId = _currentFolderId.value ?: _rootId.value
        if (parentId == null) {
            _error.value = "No folder selected"
            return
        }
        _newTextDialogVisible.value = false
        viewModelScope.launch {
            try {
                withTimeout(AppSettings.smbTimeoutMillis.value) {
                    repository.createFile(parentId, trimmed).onSuccess {
                        refreshCurrentFolder(parentId)
                    }.onFailure { e ->
                        LogBuffer.add("TabContentViewModel.createTextFile: ${e.message}")
                        _error.value = "Failed to create file: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                LogBuffer.add("TabContentViewModel.createTextFile: ${e.message}")
                _error.value = "Failed to create file: ${e.message}"
            }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _error.value = "Please enter a folder name"
            return
        }
        val parentId = _currentFolderId.value ?: _rootId.value
        if (parentId == null) {
            _error.value = "No folder selected"
            return
        }
        _newFolderDialogVisible.value = false
        viewModelScope.launch {
            try {
                withTimeout(AppSettings.smbTimeoutMillis.value) {
                    repository.createFolder(parentId, trimmed).onSuccess {
                        refreshCurrentFolder(parentId)
                    }.onFailure { e ->
                        LogBuffer.add("TabContentViewModel.createFolder: ${e.message}")
                        _error.value = "Failed to create folder: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                LogBuffer.add("TabContentViewModel.createFolder: ${e.message}")
                _error.value = "Failed to create folder: ${e.message}"
            }
        }
    }

    private fun refreshCurrentFolder(parentId: String) {
        if (parentId == _rootId.value) {
            loadRoot()
            return
        }
        val currentTree = _tree.value
        findNode(currentTree, parentId)?.let { parent ->
            loadChildren(parent, currentTree)
        }
    }

    private fun findNode(nodes: List<FileNode>, nodeId: String): FileNode? {
        for (node in nodes) {
            if (node.id == nodeId) return node
            node.children?.let { children ->
                findNode(children, nodeId)?.let { return it }
            }
        }
        return null
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

    private fun removeNode(nodes: List<FileNode>, nodeId: String): List<FileNode> {
        return nodes.mapNotNull { node ->
            when {
                node.id == nodeId -> null
                node.children != null -> node.copy(children = removeNode(node.children, nodeId))
                else -> node
            }
        }
    }

    private fun findParentId(nodes: List<FileNode>, nodeId: String): String? {
        for (node in nodes) {
            if (node.children?.any { it.id == nodeId } == true) {
                return node.id
            }
            node.children?.let { children ->
                findParentId(children, nodeId)?.let { return it }
            }
        }
        return null
    }
}
