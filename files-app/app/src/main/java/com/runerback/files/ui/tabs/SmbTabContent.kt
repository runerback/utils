package com.runerback.files.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.files.ui.components.ErrorBanner
import com.runerback.files.ui.components.FileTree

@Composable
fun SmbTabContent(
    viewModel: SmbTabContentViewModel,
    modifier: Modifier = Modifier
) {
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val multiSelectActive by viewModel.multiSelectActive.collectAsStateWithLifecycle()
    val selectedNodeIds by viewModel.selectedNodeIds.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        error?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            FileTree(
                nodes = tree,
                isLoading = isLoading,
                selectionMode = multiSelectActive,
                selectedIds = selectedNodeIds,
                currentFolderId = currentFolderId,
                onToggle = { viewModel.toggleNode(it) },
                onSelect = { viewModel.selectNode(it) },
                onToggleSelection = { viewModel.toggleNodeSelection(it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
