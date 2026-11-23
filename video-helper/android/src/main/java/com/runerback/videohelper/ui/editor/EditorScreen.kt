package com.runerback.videohelper.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.runerback.videohelper.ui.components.LoadingIndicator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExportMenu by remember { mutableStateOf(false) }
    var showGifDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message, uiState.error) {
        val text = uiState.error ?: uiState.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Project ${uiState.projectId.take(8)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val busy = uiState.runningJob != null
                    IconButton(onClick = viewModel::renderPreview, enabled = !busy) {
                        if (uiState.runningJob == "preview") {
                            LoadingIndicator(contentDescription = "Preview")
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Preview")
                        }
                    }
                    Box {
                        IconButton(onClick = { showExportMenu = true }, enabled = !busy) {
                            if (uiState.runningJob == "export" || uiState.runningJob == "export_gif") {
                                LoadingIndicator(contentDescription = "Export as…")
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = "Export as…")
                            }
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export MP4") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.exportMp4()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export GIF") },
                                onClick = {
                                    showExportMenu = false
                                    showGifDialog = true
                                },
                            )
                        }
                    }
                },
            )

            if (uiState.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                VideoPlayer(url = uiState.playerUrl)

                if (uiState.runningJob != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Running ${uiState.runningJob}…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val duration = uiState.metadata?.duration ?: 0.0

                    TrimSection(uiState, duration, viewModel)
                    CropSection(uiState, viewModel)
                    RotateSection(uiState, viewModel)
                    SpeedSection(uiState, viewModel)
                    ResizeSection(uiState, viewModel)
                    FreezeFrameSection(uiState, duration, viewModel)
                    SceneSplitSection(uiState, viewModel)
                    if (uiState.previewParts.isNotEmpty()) {
                        ClipsSection(uiState, viewModel)
                    }
                    ResultSection(uiState, viewModel)
                }
            }
        }

        if (showGifDialog) {
            ExportGifDialog(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { showGifDialog = false },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun VideoPlayer(url: String?) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(url) {
        if (url != null) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> if (text.all { it.isDigit() || it == '.' }) onValueChange(text) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun formatSeconds(value: Double): String = String.format(Locale.US, "%.1fs", value)

// ---------- Trim ----------

@Composable
private fun TrimSection(uiState: EditorUiState, duration: Double, viewModel: EditorViewModel) {
    SectionCard("Trim") {
        if (duration <= 0) return@SectionCard
        val start = uiState.state.trim.start
        val end = if (uiState.state.trim.end > 0) uiState.state.trim.end else duration
        Text("${formatSeconds(start)} – ${formatSeconds(end)} / ${formatSeconds(duration)}")
        RangeSlider(
            value = start.toFloat()..end.toFloat(),
            onValueChange = { range ->
                viewModel.updateState { state ->
                    val newEnd = if (range.endInclusive >= duration) 0.0 else range.endInclusive.toDouble()
                    state.copy(trim = state.trim.copy(start = range.start.toDouble(), end = newEnd))
                }
            },
            valueRange = 0f..duration.toFloat(),
        )
    }
}

// ---------- Crop ----------

@Composable
private fun CropSection(uiState: EditorUiState, viewModel: EditorViewModel) {
    SectionCard("Crop") {
        val crop = uiState.state.crop
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.state.cropEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateState { it.copy(cropEnabled = enabled) }
                },
            )
        }
        if (uiState.state.cropEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("x", crop.x.takeIf { it > 0 }?.toString() ?: "",
                    { v -> viewModel.updateState { it.copy(crop = crop.copy(x = v.toIntOrNull() ?: 0)) } },
                    Modifier.weight(1f))
                NumberField("y", crop.y.takeIf { it > 0 }?.toString() ?: "",
                    { v -> viewModel.updateState { it.copy(crop = crop.copy(y = v.toIntOrNull() ?: 0)) } },
                    Modifier.weight(1f))
                NumberField("w", crop.width.takeIf { it > 0 }?.toString() ?: "",
                    { v -> viewModel.updateState { it.copy(crop = crop.copy(width = v.toIntOrNull() ?: 0)) } },
                    Modifier.weight(1f))
                NumberField("h", crop.height.takeIf { it > 0 }?.toString() ?: "",
                    { v -> viewModel.updateState { it.copy(crop = crop.copy(height = v.toIntOrNull() ?: 0)) } },
                    Modifier.weight(1f))
            }
        }
    }
}

// ---------- Rotate ----------

@Composable
private fun RotateSection(uiState: EditorUiState, viewModel: EditorViewModel) {
    SectionCard("Rotate") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 90, 180, 270).forEachIndexed { quarterTurns, degrees ->
                FilterChip(
                    selected = uiState.state.rotation.quarterTurns == quarterTurns,
                    onClick = {
                        viewModel.updateState {
                            it.copy(rotation = it.rotation.copy(quarterTurns = quarterTurns))
                        }
                    },
                    label = { Text("$degrees°") },
                )
            }
        }
    }
}

// ---------- Speed ----------

@Composable
private fun SpeedSection(uiState: EditorUiState, viewModel: EditorViewModel) {
    SectionCard("Speed") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5, 1.0, 1.5, 2.0, 5.0, 10.0).forEach { speed ->
                FilterChip(
                    selected = uiState.state.speed == speed,
                    onClick = { viewModel.updateState { it.copy(speed = speed) } },
                    label = { Text("${speed}x") },
                )
            }
        }
    }
}

// ---------- Resize ----------

@Composable
private fun ResizeSection(uiState: EditorUiState, viewModel: EditorViewModel) {
    SectionCard("Resize") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Limit longest side", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.state.resizeMax != null,
                onCheckedChange = { enabled ->
                    viewModel.updateState { it.copy(resizeMax = if (enabled) 1080 else null) }
                },
            )
        }
        uiState.state.resizeMax?.let { resizeMax ->
            NumberField(
                label = "Max side (px)",
                value = resizeMax.toString(),
                onValueChange = { v ->
                    viewModel.updateState { it.copy(resizeMax = v.toIntOrNull()?.takeIf { n -> n > 0 }) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------- Freeze frame ----------

@Composable
private fun FreezeFrameSection(uiState: EditorUiState, duration: Double, viewModel: EditorViewModel) {
    SectionCard("Freeze frame") {
        val freeze = uiState.state.freezeFrame
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", modifier = Modifier.weight(1f))
            Switch(
                checked = freeze.enabled,
                onCheckedChange = { enabled ->
                    viewModel.updateState { it.copy(freezeFrame = freeze.copy(enabled = enabled)) }
                },
            )
        }
        if (freeze.enabled) {
            Text("At ${formatSeconds(freeze.timestamp)} for ${formatSeconds(freeze.duration)}")
            if (duration > 0) {
                Slider(
                    value = freeze.timestamp.toFloat(),
                    onValueChange = { v ->
                        viewModel.updateState { it.copy(freezeFrame = freeze.copy(timestamp = v.toDouble())) }
                    },
                    valueRange = 0f..duration.toFloat(),
                )
            }
            NumberField(
                label = "Duration (s)",
                value = freeze.duration.toString(),
                onValueChange = { v ->
                    viewModel.updateState {
                        it.copy(freezeFrame = freeze.copy(duration = v.toDoubleOrNull() ?: freeze.duration))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------- Scene split ----------

@Composable
private fun SceneSplitSection(uiState: EditorUiState, viewModel: EditorViewModel) {
    SectionCard("Scene split") {
        val split = uiState.state.sceneSplit
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", modifier = Modifier.weight(1f))
            Switch(
                checked = split.enabled,
                onCheckedChange = { enabled ->
                    viewModel.updateState { it.copy(sceneSplit = split.copy(enabled = enabled)) }
                },
            )
        }
        if (split.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = split.detector == "ffmpeg",
                    onClick = { viewModel.updateState { it.copy(sceneSplit = split.copy(detector = "ffmpeg")) } },
                    label = { Text("FFmpeg") },
                )
                FilterChip(
                    selected = split.detector == "ai",
                    onClick = { viewModel.updateState { it.copy(sceneSplit = split.copy(detector = "ai")) } },
                    label = { Text("AI (TransNetV2)") },
                )
            }

            if (split.detector == "ffmpeg") {
                Text("Threshold: ${"%.2f".format(split.threshold)}")
                Slider(
                    value = split.threshold.toFloat(),
                    onValueChange = { v ->
                        viewModel.updateState { it.copy(sceneSplit = split.copy(threshold = v.toDouble())) }
                    },
                    valueRange = 0.05f..1f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fixed clip length", modifier = Modifier.weight(1f))
                    Switch(
                        checked = split.fixedLengthEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateState { it.copy(sceneSplit = split.copy(fixedLengthEnabled = enabled)) }
                        },
                    )
                }
            } else {
                Text("AI sensitivity: ${"%.2f".format(split.aiSensitivity)}")
                Slider(
                    value = split.aiSensitivity.toFloat(),
                    onValueChange = { v ->
                        viewModel.updateState { it.copy(sceneSplit = split.copy(aiSensitivity = v.toDouble())) }
                    },
                    valueRange = 0.05f..1f,
                )
            }

            if (split.fixedLengthEnabled && split.detector == "ffmpeg") {
                NumberField(
                    label = "Clip length (s)",
                    value = split.fixedClipLength.toString(),
                    onValueChange = { v ->
                        viewModel.updateState {
                            it.copy(sceneSplit = split.copy(fixedClipLength = v.toDoubleOrNull() ?: split.fixedClipLength))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "Min (s)",
                        value = split.minClipLength.toString(),
                        onValueChange = { v ->
                            viewModel.updateState {
                                it.copy(sceneSplit = split.copy(minClipLength = v.toDoubleOrNull() ?: split.minClipLength))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        label = "Max (s)",
                        value = split.maxClipLength.toString(),
                        onValueChange = { v ->
                            viewModel.updateState {
                                it.copy(sceneSplit = split.copy(maxClipLength = v.toDoubleOrNull() ?: split.maxClipLength))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---------- Preview clips ----------

@Composable
private fun ClipsSection(uiState: EditorUiState, viewModel: EditorViewModel) {
    SectionCard("Clips (tap to play, check to include in export)") {
        val selected = uiState.state.sceneSplit.selectedClipIndexes
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            uiState.previewParts.forEach { part ->
                val isSelected = selected.isEmpty() || part.index in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = part.index in selected,
                        onClick = { viewModel.toggleClipSelection(part.index) },
                        label = { Text("#${part.index}") },
                    )
                    OutlinedButton(onClick = { viewModel.playPreviewPart(part) }) {
                        Text("${formatSeconds(part.start)} – ${formatSeconds(part.end)}${if (isSelected) "" else " (excluded)"}")
                    }
                }
            }
            OutlinedButton(onClick = viewModel::playOriginal) {
                Text("Back to original")
            }
        }
    }
}

// ---------- Result ----------

@Composable
private fun ResultSection(uiState: EditorUiState, viewModel: EditorViewModel) {
    val result = uiState.exportResult ?: return
    SectionCard("Result") {
        val size = result.totalOutputSizeBytes ?: result.outputSizeBytes
        Text(
            buildString {
                append("Export done")
                if (result.parts.isNotEmpty()) append(" (${result.parts.size} parts)")
                size?.let { append(" · ${formatBytes(it)}") }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = viewModel::downloadExport,
            enabled = uiState.runningJob == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Download to device")
        }
    }
}

// ---------- Export GIF dialog ----------

@Composable
private fun ExportGifDialog(
    uiState: EditorUiState,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Export GIF", style = MaterialTheme.typography.titleLarge)

                OutlinedButton(
                    onClick = viewModel::estimateGif,
                    enabled = uiState.runningJob == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Estimate GIF size")
                }
                uiState.gifEstimate?.let { estimate ->
                    Text(
                        "Estimated GIF size: ${formatBytes(estimate.estimatedSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            viewModel.exportGif()
                            onDismiss()
                        },
                        enabled = uiState.runningJob == null,
                    ) {
                        Text("Export")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1 shl 10 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
