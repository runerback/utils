package com.runerback.files.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.files.BuildConfig
import com.runerback.files.data.model.FileSource
import com.runerback.files.data.settings.AppSettings
import com.runerback.files.ui.components.LogViewDialog
import com.runerback.files.ui.components.NewTextFileDialog
import com.runerback.files.ui.components.SettingsDialog
import com.runerback.files.ui.components.SmbServerDialog
import com.runerback.files.ui.icons.FluentuiSystemIconsCopy
import com.runerback.files.ui.icons.FluentuiSystemIconsCut
import com.runerback.files.ui.icons.FluentuiSystemIconsSelectAllOff
import com.runerback.files.ui.icons.FluentuiSystemIconsTextAdd
import com.runerback.files.ui.tabs.LocalTabContent
import com.runerback.files.ui.tabs.LocalTabContentViewModel
import com.runerback.files.ui.tabs.SmbTabContent
import com.runerback.files.ui.tabs.SmbTabContentViewModel
import com.runerback.files.ui.tabs.TabContentViewModel

@Composable
fun FilesScreen(
    viewModel: FilesViewModel = hiltViewModel()
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsStateWithLifecycle()
    val smbDialogState by viewModel.smbDialogState.collectAsStateWithLifecycle()
    val settingsDialogVisible by viewModel.settingsDialogVisible.collectAsStateWithLifecycle()
    val smbTimeoutMillis by AppSettings.smbTimeoutMillis.collectAsStateWithLifecycle()
    var showLogView by remember { mutableStateOf(false) }

    val safeSelectedIndex = remember(selectedTabIndex, tabs.size) {
        if (tabs.isEmpty()) 0 else selectedTabIndex.coerceIn(0, tabs.size - 1)
    }

    val activeTab = if (tabs.isEmpty()) null else tabs.getOrNull(safeSelectedIndex)
    val activeTabId = activeTab?.id

    val contentViewModel = remember(activeTab) {
        activeTabId?.let { viewModel.getTabViewModel(it) }
    }

    val isMultiSelectActive by if (contentViewModel != null) {
        contentViewModel.multiSelectActive.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(false) }
    }

    val newTextDialogVisible by if (contentViewModel != null) {
        contentViewModel.newTextDialogVisible.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(false) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        modifier = Modifier.offset(y = (-1).dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Files",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = { viewModel.openSettingsDialog() }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                        IconButton(onClick = { showLogView = true }) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Logs"
                            )
                        }
                        IconButton(onClick = { viewModel.openAddSmbDialog() }) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "Add Server"
                            )
                        }
                    }
                }

                if (tabs.isEmpty()) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )
                } else {
                    key(tabs.size) {
                        ScrollableTabRow(
                            selectedTabIndex = safeSelectedIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                            edgePadding = 0.dp
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                var showMenu by remember { mutableStateOf(false) }
                                Tab(
                                    selected = safeSelectedIndex == index,
                                    onClick = { viewModel.selectTab(index) },
                                    text = { Text(tab.name) },
                                    modifier = Modifier
                                        .height(26.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = { showMenu = true }
                                            )
                                        },
                                )
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    if (tab.source is FileSource.Smb) {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = {
                                                showMenu = false
                                                viewModel.openEditSmbDialog(tab.id)
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Remove") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.removeTab(index)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* TODO: copy */ }) {
                    Icon(
                        imageVector = FluentuiSystemIconsCopy,
                        contentDescription = "Copy"
                    )
                }
                IconButton(onClick = { /* TODO: cut */ }) {
                    Icon(
                        imageVector = FluentuiSystemIconsCut,
                        contentDescription = "Cut"
                    )
                }
                IconButton(onClick = { /* TODO: delete */ }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete"
                    )
                }
                IconButton(
                    onClick = { /* TODO: rename */ },
                    enabled = !isMultiSelectActive
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename"
                    )
                }
                IconButton(onClick = { contentViewModel?.toggleMultiSelect() }) {
                    Icon(
                        imageVector = FluentuiSystemIconsSelectAllOff,
                        contentDescription = "Select",
                        tint = if (isMultiSelectActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        }
                    )
                }
                IconButton(
                    onClick = { contentViewModel?.openNewTextDialog() },
                    enabled = !isMultiSelectActive
                ) {
                    Icon(
                        imageVector = FluentuiSystemIconsTextAdd,
                        contentDescription = "New Text"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                activeTab == null -> {
                    PlaceholderState()
                }
                activeTab.source is FileSource.Local -> {
                    val localVm = contentViewModel as? LocalTabContentViewModel
                    if (localVm != null) {
                        LocalTabContent(
                            viewModel = localVm,
                            onSetLocalRoot = { uri -> viewModel.setLocalRoot(activeTab.id, uri) }
                        )
                    }
                }
                activeTab.source is FileSource.Smb -> {
                    val smbVm = contentViewModel as? SmbTabContentViewModel
                    if (smbVm != null) {
                        SmbTabContent(viewModel = smbVm)
                    }
                }
                else -> {
                    PlaceholderState()
                }
            }
        }
    }

    if (showLogView) {
        LogViewDialog(onDismiss = { showLogView = false })
    }

    when (val state = smbDialogState) {
        is SmbDialogState.Add -> {
            SmbServerDialog(
                initialConfig = FileSource.Smb(
                    name = "",
                    host = "",
                    share = "",
                    username = "",
                    password = "",
                ),
                onTestConnection = { viewModel.testSmbConnection(it) },
                onSave = { viewModel.saveSmbServer(it) },
                onDismiss = { viewModel.dismissSmbDialog() },
            )
        }
        is SmbDialogState.Edit -> {
            SmbServerDialog(
                initialConfig = state.config,
                onTestConnection = { viewModel.testSmbConnection(it) },
                onSave = { viewModel.saveSmbServer(it) },
                onDismiss = { viewModel.dismissSmbDialog() },
            )
        }
        SmbDialogState.Hidden -> { /* no-op */ }
    }

    if (settingsDialogVisible) {
        SettingsDialog(
            currentSmbTimeoutMillis = smbTimeoutMillis,
            onSave = { viewModel.saveSmbTimeoutMillis(it) },
            onDismiss = { viewModel.dismissSettingsDialog() },
        )
    }

    if (newTextDialogVisible) {
        NewTextFileDialog(
            onSave = { contentViewModel?.createTextFile(it) },
            onDismiss = { contentViewModel?.dismissNewTextDialog() },
        )
    }
}

@Composable
private fun PlaceholderState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(160.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Folders / files will appear here",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
