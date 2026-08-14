package com.runerback.files.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.key
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.files.BuildConfig
import com.runerback.files.data.model.FileSource
import com.runerback.files.data.settings.AppSettings
import com.runerback.files.ui.components.FileTree
import com.runerback.files.ui.components.LogBuffer
import com.runerback.files.ui.components.LogViewDialog
import com.runerback.files.ui.components.SettingsDialog
import com.runerback.files.ui.components.SmbServerDialog
import com.runerback.files.ui.icons.FluentuiSystemIconsCopy
import com.runerback.files.ui.icons.FluentuiSystemIconsCut
import com.runerback.files.ui.icons.FluentuiSystemIconsSelectAllOff
import com.runerback.files.ui.icons.FluentuiSystemIconsTextAdd

@Composable
fun FilesScreen(
    viewModel: FilesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsStateWithLifecycle()
    val trees by viewModel.trees.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val multiSelectActive by viewModel.multiSelectActive.collectAsStateWithLifecycle()
    val selectedNodeIds by viewModel.selectedNodeIds.collectAsStateWithLifecycle()
    val smbDialogState by viewModel.smbDialogState.collectAsStateWithLifecycle()
    val settingsDialogVisible by viewModel.settingsDialogVisible.collectAsStateWithLifecycle()
    val smbTimeoutMillis by AppSettings.smbTimeoutMillis.collectAsStateWithLifecycle()
    var showLogView by remember { mutableStateOf(false) }

    var hasFullStorageAccess by remember {
        mutableStateOf(checkFullStorageAccess(context))
    }
    var hasBasicPermission by remember {
        mutableStateOf(checkBasicStoragePermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasBasicPermission = checkBasicStoragePermission(context)
        hasFullStorageAccess = checkFullStorageAccess(context)
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasFullStorageAccess = checkFullStorageAccess(context)
        hasBasicPermission = checkBasicStoragePermission(context)
    }

    val safeSelectedIndex = remember(selectedTabIndex, tabs.size) {
        if (tabs.isEmpty()) 0 else selectedTabIndex.coerceIn(0, tabs.size - 1)
    }

    LaunchedEffect(hasFullStorageAccess, hasBasicPermission, tabs, safeSelectedIndex) {
        val activeTab = tabs.getOrNull(safeSelectedIndex)
        if (activeTab?.source is FileSource.Local) {
            val localSource = activeTab.source as FileSource.Local
            if (localSource.rootUri.toString().isEmpty()) {
                val canBrowse = hasFullStorageAccess || (
                    hasBasicPermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                )
                if (canBrowse) {
                    val storageRoot = Uri.fromFile(Environment.getExternalStorageDirectory())
                    viewModel.setLocalRoot(activeTab.id, storageRoot)
                }
            }
        }
    }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        try {
            val activeTab = tabs.getOrNull(safeSelectedIndex)
            if (uri != null && activeTab != null) {
                viewModel.setLocalRoot(activeTab.id, uri)
            }
        } catch (e: Exception) {
            LogBuffer.add("FilesScreen document picker: ${e.stackTraceToString()}")
        }
    }

    val activeTab = if (tabs.isEmpty()) null else tabs.getOrNull(safeSelectedIndex)
    val activeTabId = activeTab?.id
    val isMultiSelectActive = activeTabId?.let { multiSelectActive[it] ?: false } ?: false
    val activeSelectedIds = activeTabId?.let { selectedNodeIds[it] ?: emptySet() } ?: emptySet()
    val activeTree = activeTab?.let { trees[it.id] }

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
                    Row (verticalAlignment = Alignment.Bottom) {
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
                IconButton(onClick = { activeTab?.let { viewModel.toggleMultiSelect(it.id) } }) {
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
                    onClick = { /* TODO: new text file */ },
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
            Column(modifier = Modifier.fillMaxSize()) {
                error?.let { message ->
                    ErrorBanner(
                        message = message,
                        onDismiss = { viewModel.clearError() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        activeTab == null -> {
                            PlaceholderState()
                        }
                        activeTab.source is FileSource.Local && (activeTab.source as FileSource.Local).rootUri.toString().isEmpty() -> {
                            LocalEmptyState(
                                hasFullStorageAccess = hasFullStorageAccess,
                                hasBasicPermission = hasBasicPermission,
                                onGrantFullAccess = {
                                    requestFullStorageAccess(context, settingsLauncher)
                                },
                                onRequestBasicPermission = {
                                    requestBasicStoragePermission(permissionLauncher)
                                },
                                onChooseFolder = { openDocumentTreeLauncher.launch(null) }
                            )
                        }
                        activeTree != null -> {
                            FileTree(
                                nodes = activeTree,
                                isLoading = false,
                                selectionMode = isMultiSelectActive,
                                selectedIds = activeSelectedIds,
                                onToggle = { node ->
                                    viewModel.toggleNode(activeTab.id, node)
                                },
                                onSelect = { node ->
                                    if (isMultiSelectActive && !node.isDirectory) {
                                        viewModel.toggleNodeSelection(activeTab.id, node)
                                    } else {
                                        viewModel.selectNode(activeTab.id, node)
                                    }
                                },
                                onToggleSelection = { node ->
                                    viewModel.toggleNodeSelection(activeTab.id, node)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        activeTab.source is FileSource.Smb -> {
                            FileTree(
                                nodes = emptyList(),
                                isLoading = true,
                                selectionMode = false,
                                selectedIds = emptySet(),
                                onToggle = {},
                                onSelect = {},
                                onToggleSelection = {},
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            PlaceholderState()
                        }
                    }
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
}

@Composable
private fun LocalEmptyState(
    hasFullStorageAccess: Boolean,
    hasBasicPermission: Boolean,
    onGrantFullAccess: () -> Unit,
    onRequestBasicPermission: () -> Unit,
    onChooseFolder: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = when {
                    hasFullStorageAccess -> "Loading storage..."
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        "To browse all files on Android 11+, grant All Files Access. Or choose a single folder."
                    hasBasicPermission ->
                        "Permission granted. Loading storage..."
                    else ->
                        "Allow storage access to browse local files, or choose a single folder"
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasFullStorageAccess) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Button(onClick = onGrantFullAccess) {
                        Text("Grant all-files access")
                    }
                } else if (!hasBasicPermission) {
                    Button(onClick = onRequestBasicPermission) {
                        Text("Allow storage access")
                    }
                }
                Button(onClick = onChooseFolder) {
                    Text("Choose folder")
                }
            }
        }
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

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

private fun checkFullStorageAccess(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        checkBasicStoragePermission(context)
    }
}

private fun checkBasicStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_IMAGES
        ) == PermissionChecker.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_VIDEO
        ) == PermissionChecker.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_AUDIO
        ) == PermissionChecker.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PermissionChecker.PERMISSION_GRANTED
    }
}

private fun requestFullStorageAccess(
    context: android.content.Context,
    settingsLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        settingsLauncher.launch(intent)
    }
}

private fun requestBasicStoragePermission(
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO
            )
        )
    } else {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }
}
