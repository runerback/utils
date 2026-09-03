package com.runerback.queuehelper.ui.presets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.queuehelper.QueueHelperApplication
import com.runerback.queuehelper.data.model.Preset
import com.runerback.queuehelper.ui.components.LogViewDialog
import com.runerback.queuehelper.ui.icons.BootstrapBoxArrowInDown
import com.runerback.queuehelper.ui.icons.BootstrapBoxArrowInUp
import com.runerback.queuehelper.ui.icons.FluentuiSystemIconsFolderZip
import com.runerback.queuehelper.ui.icons.FluentuiSystemIconsSelectAllOff
import com.runerback.queuehelper.ui.icons.TablerLogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetListScreen(
    onEditPreset: (Int) -> Unit,
    onPackPreset: (Int) -> Unit,
    onOpenGlobalPack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QueueHelperApplication
    val viewModel: PresetListViewModel = viewModel(
        factory = PresetListViewModel.Factory(app.presetRepository, app.templateLoader)
    )
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadPresets()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PresetListViewModel.PresetListEvent.NavigateToEdit -> onEditPreset(event.presetId)
                is PresetListViewModel.PresetListEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    var showLogView by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(viewModel.exportPresets().toByteArray())
                }
                viewModel.toggleSelectionMode()
            }.onFailure { e ->
                com.runerback.queuehelper.ui.components.LogBuffer.add(
                    "PresetListScreen.export: ${e.stackTraceToString()}"
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                val json = context.contentResolver.openInputStream(it)?.use { stream ->
                    stream.bufferedReader().readText()
                }.orEmpty()
                viewModel.importPresets(json)
            }.onFailure { e ->
                com.runerback.queuehelper.ui.components.LogBuffer.add(
                    "PresetListScreen.import: ${e.stackTraceToString()}"
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue Helper") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.openCreateDialog() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create New"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Icon(
                            imageVector = BootstrapBoxArrowInDown,
                            contentDescription = "Import presets"
                        )
                    }
                    if (viewModel.presets.isNotEmpty()) {
                        IconButton(onClick = { viewModel.toggleSelectionMode() }) {
                            Icon(
                                imageVector = FluentuiSystemIconsSelectAllOff,
                                contentDescription = "Select presets",
                                tint = if (viewModel.selectionMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    IconButton(onClick = onOpenGlobalPack) {
                        Icon(
                            imageVector = FluentuiSystemIconsFolderZip,
                            contentDescription = "Open global pack tasks"
                        )
                    }
                    IconButton(onClick = { showLogView = true }) {
                        Icon(
                            imageVector = TablerLogs,
                            contentDescription = "Logs"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewModel.selectionMode && viewModel.selectedPresetIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { exportLauncher.launch("presets.json") },
                    icon = {
                        Icon(
                            imageVector = BootstrapBoxArrowInUp,
                            contentDescription = "Export"
                        )
                    },
                    text = { Text("Export") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.presets.isEmpty()) {
                Text(
                    text = "No presets yet. Tap + to create one.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.presets, key = { it.id }) { preset ->
                        PresetListItem(
                            preset = preset,
                            selectionMode = viewModel.selectionMode,
                            selected = preset.id in viewModel.selectedPresetIds,
                            onToggleSelected = { viewModel.setSelected(preset.id, preset.id !in viewModel.selectedPresetIds) },
                            onEdit = { onEditPreset(preset.id) },
                            onDuplicate = { viewModel.duplicatePreset(preset) },
                            onPack = { onPackPreset(preset.id) },
                            onDelete = { viewModel.deletePreset(preset) }
                        )
                    }
                }
            }

            if (viewModel.showCreateDialog) {
                CreatePresetDialog(
                    templates = app.templateLoader.load().keys.toList()
                        .map { modelType ->
                            modelType to app.templateLoader.defaultName(modelType)
                        },
                    onDismiss = { viewModel.closeCreateDialog() },
                    onCreate = { name, modelType ->
                        viewModel.createPreset(name, modelType)
                    }
                )
            }

            if (showLogView) {
                LogViewDialog(onDismiss = { showLogView = false })
            }
        }
    }
}

@Composable
private fun PresetListItem(
    preset: Preset,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onPack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelected() }
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (selectionMode) Modifier.clickable { onToggleSelected() } else Modifier)
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = preset.modelType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit, enabled = !selectionMode) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit preset"
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDuplicate, enabled = !selectionMode) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Duplicate preset"
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onPack, enabled = !selectionMode) {
                Icon(
                    imageVector = FluentuiSystemIconsFolderZip,
                    contentDescription = "Pack preset"
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDelete, enabled = !selectionMode) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete preset"
                )
            }
        }
    }
}
