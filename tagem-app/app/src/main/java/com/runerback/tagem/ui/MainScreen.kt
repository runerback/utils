package com.runerback.tagem.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.tagem.data.ImageStore
import com.runerback.tagem.data.TagEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: GalleryViewModel.UiState,
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
) {
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
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
}
