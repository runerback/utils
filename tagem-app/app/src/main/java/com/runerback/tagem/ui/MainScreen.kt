package com.runerback.tagem.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.tagem.data.ImageStore
import com.runerback.tagem.data.TagEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: GalleryViewModel.UiState,
    logs: List<String>,
    onToggleTagPanel: () -> Unit,
    onSelectTag: (Long?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectImage: (Uri) -> Unit,
    onDismissEditor: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Long) -> Unit,
    onShareImage: (Uri) -> Unit,
    onRefresh: () -> Unit,
    onToggleGifsOnly: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearLogs: () -> Unit,
) {
    var showLogViewer by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val subtitle = when {
                            uiState.selectedTagId != null -> {
                                val tag = uiState.tags.find { it.id == uiState.selectedTagId }
                                "Tag: ${tag?.name ?: ""}"
                            }
                            else -> "All Photos"
                        }
                        Text(
                            text = "TagEm — $subtitle",
                            modifier = Modifier.clickable(onClick = onToggleTagPanel),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onToggleTagPanel) {
                            Icon(Icons.Default.Menu, contentDescription = "Tags")
                        }
                    },
                    actions = {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export tags") },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import tags") },
                                onClick = {
                                    menuExpanded = false
                                    onImport()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    menuExpanded = false
                                    onRefresh()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("View logs") },
                                onClick = {
                                    menuExpanded = false
                                    showLogViewer = true
                                },
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                AnimatedVisibility(
                    visible = uiState.tagPanelOpen,
                    enter = expandVertically(animationSpec = tween(200)),
                    exit = shrinkVertically(animationSpec = tween(200)),
                ) {
                    TagFilterPanel(
                        tags = uiState.tags,
                        selectedTagId = uiState.selectedTagId,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onSelectTag = onSelectTag,
                        showGifsOnly = uiState.showGifsOnly,
                        onToggleGifsOnly = onToggleGifsOnly,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    GalleryScreen(
                        images = uiState.filteredImages,
                        tagCounts = uiState.tagCounts,
                        onImageClick = onSelectImage,
                        onImageLongPress = onShareImage,
                    )

                    if (uiState.tagPanelOpen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = onToggleTagPanel,
                                ),
                        )
                    }
                }
            }
        }

        if (uiState.isExporting || uiState.isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        if (uiState.isExporting) "Exporting..." else "Importing...",
                    )
                }
            }
        }
    }

    uiState.selectedImageUri?.let { uri ->
        val selectedImage = uiState.images.find { it.uri == uri }
        TagEditorDialog(
            uri = uri,
            isGif = selectedImage?.isGif ?: false,
            tags = uiState.selectedImageTags,
            allTags = uiState.tags,
            onDismiss = onDismissEditor,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
        )
    }

    if (showLogViewer) {
        LogViewerDialog(
            logs = logs,
            onDismiss = { showLogViewer = false },
            onClear = onClearLogs,
        )
    }
}
