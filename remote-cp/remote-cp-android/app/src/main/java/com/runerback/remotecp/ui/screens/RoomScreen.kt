package com.runerback.remotecp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.runerback.remotecp.data.model.ImageAttachment
import com.runerback.remotecp.data.model.VideoAttachment
import com.runerback.remotecp.ui.components.Composer
import com.runerback.remotecp.ui.components.Feed
import com.runerback.remotecp.ui.components.ImagePreviewDialog
import com.runerback.remotecp.ui.components.SettingsDialog
import com.runerback.remotecp.ui.components.VideoPreviewDialog
import com.runerback.remotecp.ui.viewmodel.RoomViewModel
import com.runerback.remotecp.util.saveToDownloads
import kotlinx.coroutines.launch

@Composable
fun RoomScreen(viewModel: RoomViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var previewingImage by remember { mutableStateOf<ImageAttachment?>(null) }
    var previewingVideo by remember { mutableStateOf<VideoAttachment?>(null) }

    LaunchedEffect(uiState.statusMessage, uiState.error) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showSettings) {
                SettingsDialog(
                    currentUrl = uiState.backendUrl,
                    onDismiss = { showSettings = false },
                    onSave = { url ->
                        viewModel.updateBackendUrl(url)
                        showSettings = false
                    }
                )
            }

            previewingImage?.let { image ->
                ImagePreviewDialog(
                    image = image,
                    backendUrl = uiState.backendUrl,
                    onDismiss = { previewingImage = null }
                )
            }

            previewingVideo?.let { video ->
                VideoPreviewDialog(
                    video = video,
                    backendUrl = uiState.backendUrl,
                    onDismiss = { previewingVideo = null }
                )
            }

            // Two-panel layout on large screens, stacked on small
            Box(modifier = Modifier.weight(1f)) {
                Feed(
                    messages = uiState.messages,
                    isConnected = uiState.isConnected,
                    backendUrl = uiState.backendUrl,
                    error = uiState.error,
                    onStatus = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onOpenSettings = { showSettings = true },
                    onImageClick = { previewingImage = it },
                    onVideoClick = { previewingVideo = it },
                    onFileClick = { file ->
                        context.saveToDownloads("${uiState.backendUrl}${file.downloadUrl}", file.name)
                        scope.launch { snackbarHostState.showSnackbar("Downloading ${file.name}...") }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Composer(
                    isLoading = uiState.isLoading,
                    onSendText = { text -> viewModel.sendText(text, context) },
                    onSendMedia = { images, videos, files ->
                        viewModel.sendMedia(images, videos, files, context)
                    },
                    onOpenSettings = { showSettings = true }
                )
            }
        }
    }
}
