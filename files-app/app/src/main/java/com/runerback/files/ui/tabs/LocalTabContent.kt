package com.runerback.files.ui.tabs

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.files.ui.components.ErrorBanner
import com.runerback.files.ui.components.FileTree
import com.runerback.files.ui.components.LogBuffer

@Composable
fun LocalTabContent(
    viewModel: LocalTabContentViewModel,
    onSetLocalRoot: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val multiSelectActive by viewModel.multiSelectActive.collectAsStateWithLifecycle()
    val selectedNodeIds by viewModel.selectedNodeIds.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()

    var hasFullStorageAccess by remember {
        mutableStateOf(checkFullStorageAccess(context))
    }
    var hasBasicPermission by remember {
        mutableStateOf(checkBasicStoragePermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasBasicPermission = checkBasicStoragePermission(context)
        hasFullStorageAccess = checkFullStorageAccess(context)
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasFullStorageAccess = checkFullStorageAccess(context)
        hasBasicPermission = checkBasicStoragePermission(context)
    }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onSetLocalRoot(uri)
        }
    }

    val rootUri = viewModel.source.rootUri

    LogBuffer.add(
        "LocalTabContent: SDK=${Build.VERSION.SDK_INT}, " +
        "fullAccess=$hasFullStorageAccess, basic=$hasBasicPermission, rootUri=$rootUri"
    )

    LaunchedEffect(hasFullStorageAccess, hasBasicPermission, rootUri) {
        LogBuffer.add(
            "LocalTabContent.LaunchedEffect: rootUri=$rootUri, " +
            "fullAccess=$hasFullStorageAccess, basic=$hasBasicPermission"
        )
        if (rootUri.toString().isEmpty()) {
            val canBrowse = hasFullStorageAccess || (
                hasBasicPermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            )
            if (canBrowse) {
                val storageRoot = Uri.fromFile(Environment.getExternalStorageDirectory())
                LogBuffer.add(
                    "LocalTabContent: auto-selecting storage root " +
                    "path=${Environment.getExternalStorageDirectory()?.absolutePath}, uri=$storageRoot"
                )
                onSetLocalRoot(storageRoot)
            } else {
                LogBuffer.add("LocalTabContent: cannot auto-browse, waiting for permission or folder selection")
            }
        } else {
            LogBuffer.add("LocalTabContent: rootUri already set, skipping auto-select")
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        error?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                rootUri.toString().isEmpty() -> {
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
                else -> {
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
                    hasBasicPermission -> "Permission granted. Loading storage..."
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
