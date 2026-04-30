package com.runerback.screenrecorder.ui

import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runerback.screenrecorder.data.FrameRatePreset
import com.runerback.screenrecorder.data.RecorderSettings
import com.runerback.screenrecorder.data.RecorderUiState
import com.runerback.screenrecorder.data.RecordingStatus
import com.runerback.screenrecorder.data.RecordingStore
import com.runerback.screenrecorder.data.ResolutionPreset
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    uiState: RecorderUiState,
    settings: RecorderSettings,
    recordings: List<RecordingStore.RecordingItem>,
    overlayEnabled: Boolean,
    onEnterRecording: () -> Unit,
    onResolutionPresetSelected: (ResolutionPreset) -> Unit,
    onFrameRatePresetSelected: (FrameRatePreset) -> Unit,
    onCaptureSystemAudioChanged: (Boolean) -> Unit,
    onRefreshRecordings: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenRecording: (Uri) -> Unit,
    onShareRecording: (Uri) -> Unit,
    onDeleteRecording: (Uri) -> Unit,
    onDismissError: () -> Unit,
) {
    var pendingDeletion by remember { mutableStateOf<RecordingStore.RecordingItem?>(null) }
    val statusText = when {
        uiState.status == RecordingStatus.IDLE && uiState.isToolboxVisible -> "Toolbox is ready"
        uiState.status == RecordingStatus.IDLE -> "Ready"
        uiState.status == RecordingStatus.PREPARING -> "Preparing the recorder"
        uiState.status == RecordingStatus.RECORDING -> "Recording is active"
        else -> "Stopping the recorder"
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Screen Recorder") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Recording settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(text = "Resolution")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ResolutionPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = settings.resolutionPreset == preset,
                                    onClick = { onResolutionPresetSelected(preset) },
                                    label = { Text(text = preset.label) },
                                )
                            }
                        }
                        Text(text = "Frame rate")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FrameRatePreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = settings.frameRatePreset == preset,
                                    onClick = { onFrameRatePresetSelected(preset) },
                                    label = { Text(text = preset.label) },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Record system sound")
                                Text(
                                    text = "When enabled, the recorder captures app and media playback audio.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = settings.captureSystemAudio,
                                onCheckedChange = onCaptureSystemAudioChanged,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Recording toolbox",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(text = statusText)
                        Text(
                            text = when {
                                uiState.status == RecordingStatus.RECORDING && uiState.isAudioCaptureActive ->
                                    "System sound capture is active."
                                uiState.status == RecordingStatus.RECORDING ->
                                    "Recording is active without system sound."
                                settings.captureSystemAudio ->
                                    "System sound capture will be requested when recording starts."
                                else ->
                                    "Recording will be video-only until system sound is enabled."
                            },
                        )
                        uiState.lastOutputUri?.let { lastOutput ->
                            Text(text = "Last saved recording: $lastOutput")
                        }
                        if (overlayEnabled) {
                            Button(
                                onClick = onEnterRecording,
                                enabled = uiState.status == RecordingStatus.IDLE && !uiState.isToolboxVisible,
                            ) {
                                Text(text = "Enter Recording")
                            }
                            if (uiState.isToolboxVisible) {
                                Text(
                                    text = "Use the floating toolbox to start, stop, or exit recording.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            Text(
                                text = "Enable overlay permission to use the floating recording toolbox.",
                            )
                            OutlinedButton(onClick = onRequestOverlayPermission) {
                                Text(text = "Enable floating control")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Saved recordings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onRefreshRecordings) {
                        Text(text = "Refresh")
                    }
                }
            }

            if (recordings.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No recordings found yet. Use Enter Recording to open the toolbox and start a recording.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(recordings, key = { it.uri.toString() }) { item ->
                    RecordingItemCard(
                        item = item,
                        onOpenRecording = { onOpenRecording(item.uri) },
                        onShareRecording = { onShareRecording(item.uri) },
                        onDeleteRecording = { pendingDeletion = item },
                    )
                }
            }
        }
    }

    uiState.errorMessage?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text(text = "OK")
                }
            },
            title = {
                Text(text = "Recording error")
            },
            text = {
                Text(text = errorMessage)
            },
        )
    }

    pendingDeletion?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecording(item.uri)
                        pendingDeletion = null
                    },
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(text = "Cancel")
                }
            },
            title = {
                Text(text = "Delete recording?")
            },
            text = {
                Text(text = "Remove ${item.displayName}? This cannot be undone.")
            },
        )
    }
}

@Composable
private fun RecordingItemCard(
    item: RecordingStore.RecordingItem,
    onOpenRecording: () -> Unit,
    onShareRecording: () -> Unit,
    onDeleteRecording: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecordingThumbnail(uri = item.uri)
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = "Captured: ${formatDate(item.dateAddedMillis)}")
            Text(text = "Size: ${formatBytes(item.sizeBytes)}")
            item.durationMillis?.let { duration ->
                Text(text = "Duration: ${formatDuration(duration)}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onOpenRecording) {
                    Text(text = "Open")
                }
                TextButton(onClick = onShareRecording) {
                    Text(text = "Share")
                }
                TextButton(onClick = onDeleteRecording) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

@Composable
private fun RecordingThumbnail(uri: Uri) {
    val context = LocalContext.current
    val thumbnail by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(640, 360), null)
            } catch (_: IOException) {
                null
            }
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Preview unavailable",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatDate(timestampMillis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampMillis))
}

private fun formatBytes(sizeBytes: Long): String {
    val megabytes = sizeBytes / 1024f / 1024f
    return String.format("%.1f MB", megabytes)
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format("%d hr %02d min %02d sec", hours, minutes % 60, seconds)
        minutes > 0 -> String.format("%d min %02d sec", minutes, seconds)
        else -> String.format("%d sec", seconds)
    }
}
