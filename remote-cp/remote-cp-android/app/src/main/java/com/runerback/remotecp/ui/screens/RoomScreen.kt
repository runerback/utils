package com.runerback.remotecp.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.runerback.remotecp.ui.components.LogViewerDialog
import com.runerback.remotecp.ui.components.FileDownloadUiState
import com.runerback.remotecp.ui.components.SettingsDialog
import com.runerback.remotecp.ui.components.VideoPreviewDialog
import com.runerback.remotecp.ui.viewmodel.RoomViewModel
import com.runerback.remotecp.util.AppLog
import com.runerback.remotecp.util.openDownload
import com.runerback.remotecp.util.saveToDownloads
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RoomScreen(viewModel: RoomViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var previewingImage by remember { mutableStateOf<ImageAttachment?>(null) }
    var previewingVideo by remember { mutableStateOf<VideoAttachment?>(null) }
    var pendingDownloads by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var fileDownloadStates by remember { mutableStateOf<Map<String, FileDownloadUiState>>(emptyMap()) }
    var downloadIdToUrl by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

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

    LaunchedEffect(Unit) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        while (true) {
            delay(200)
            val downloading = fileDownloadStates.mapNotNull { (url, state) ->
                (state as? FileDownloadUiState.Downloading)?.let { url to it.downloadId }
            }
            if (downloading.isEmpty()) continue

            val ids = downloading.map { it.second }.toLongArray()
            val query = DownloadManager.Query().setFilterById(*ids)
            val updates = mutableMapOf<String, FileDownloadUiState>()
            var completedIds = setOf<Long>()
            dm.query(query)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val url = downloadIdToUrl[id] ?: continue
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            updates[url] = FileDownloadUiState.Downloaded(id)
                            completedIds = completedIds + id
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            updates[url] = FileDownloadUiState.Failed("Download failed: $reason")
                            completedIds = completedIds + id
                        }
                        else -> {
                            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
                            updates[url] = FileDownloadUiState.Downloading(id, progress)
                        }
                    }
                }
            }
            if (updates.isNotEmpty()) {
                fileDownloadStates = fileDownloadStates + updates
            }
            if (completedIds.isNotEmpty()) {
                downloadIdToUrl = downloadIdToUrl - completedIds
            }
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                val fileName = pendingDownloads[id] ?: return
                pendingDownloads = pendingDownloads - id
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "Downloaded $fileName",
                        actionLabel = "Open",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val opened = ctx.openDownload(id)
                        if (!opened) {
                            snackbarHostState.showSnackbar("Download not ready yet.")
                        }
                    }
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun startDownload(fileName: String, url: String) {
        val downloadId = context.saveToDownloads(url, fileName)
        pendingDownloads = pendingDownloads + (downloadId to fileName)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "Downloading $fileName...",
                duration = SnackbarDuration.Short
            )
        }
    }

    fun startFileDownload(fileName: String, url: String) {
        val downloadId = context.saveToDownloads(url, fileName)
        fileDownloadStates = fileDownloadStates + (url to FileDownloadUiState.Downloading(downloadId, 0f))
        downloadIdToUrl = downloadIdToUrl + (downloadId to url)
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
                    recentUrls = uiState.recentBackendUrls,
                    onDismiss = { showSettings = false },
                    onSave = { url ->
                        viewModel.updateBackendUrl(url)
                        showSettings = false
                    }
                )
            }

            if (showLogs) {
                LogViewerDialog(
                    logs = AppLog.getLines(),
                    onDismiss = { showLogs = false }
                )
            }

            previewingImage?.let { image ->
                ImagePreviewDialog(
                    image = image,
                    backendUrl = uiState.backendUrl,
                    onDismiss = { previewingImage = null },
                    onSave = { startDownload(image.name, "${uiState.backendUrl}${image.url}") }
                )
            }

            previewingVideo?.let { video ->
                VideoPreviewDialog(
                    video = video,
                    backendUrl = uiState.backendUrl,
                    onDismiss = { previewingVideo = null },
                    onSave = { startDownload(video.name, "${uiState.backendUrl}${video.url}") }
                )
            }

            // Two-panel layout on large screens, stacked on small
            Box(modifier = Modifier.weight(1f)) {
                Feed(
                    messages = uiState.messages,
                    isConnected = uiState.isConnected,
                    isLoading = uiState.isLoading,
                    backendUrl = uiState.backendUrl,
                    error = uiState.error,
                    markdownMode = uiState.markdownMode,
                    fileDownloadStates = fileDownloadStates,
                    onToggleMarkdown = { viewModel.toggleMarkdownMode(it) },
                    onStatus = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onOpenSettings = { showSettings = true },
                    onRefresh = { viewModel.loadMessages() },
                    onImageClick = { previewingImage = it },
                    onVideoClick = { previewingVideo = it },
                    onFileClick = { file ->
                        val url = "${uiState.backendUrl}${file.downloadUrl}"
                        when (val state = fileDownloadStates[url]) {
                            is FileDownloadUiState.Downloaded -> {
                                val opened = context.openDownload(state.downloadId)
                                if (!opened) {
                                    scope.launch { snackbarHostState.showSnackbar("Download not ready yet.") }
                                }
                            }
                            is FileDownloadUiState.Downloading -> {
                                // Ignore clicks while downloading
                            }
                            else -> {
                                startFileDownload(file.name, url)
                            }
                        }
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
                    onOpenSettings = { showSettings = true },
                    onOpenLogs = { showLogs = true }
                )
            }
        }
    }
}
