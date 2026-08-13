package com.runerback.comfyuiapi.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.comfyuiapi.data.model.EditableParameter
import com.runerback.comfyuiapi.data.model.FieldType
import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.ui.components.FilePickerSection
import com.runerback.comfyuiapi.ui.components.GenerationPanel
import com.runerback.comfyuiapi.ui.components.IntFieldEditor
import com.runerback.comfyuiapi.ui.components.LogViewDialog
import com.runerback.comfyuiapi.ui.components.OptionFieldEditor
import com.runerback.comfyuiapi.ui.components.PreviewPanel
import com.runerback.comfyuiapi.ui.components.SeedFieldEditor
import com.runerback.comfyuiapi.ui.components.SettingsDialog
import com.runerback.comfyuiapi.ui.components.StringFieldEditor
import com.runerback.comfyuiapi.ui.components.UploadFieldEditor
import com.runerback.comfyuiapi.ui.components.asLong
import com.runerback.comfyuiapi.ui.components.asNumber
import com.runerback.comfyuiapi.ui.components.asString
import com.runerback.comfyuiapi.ui.components.toJsonPrimitive
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSchemaGenerator: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allOutputs by viewModel.allOutputs.collectAsStateWithLifecycle()
    var showLogView by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var parametersExpanded by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val versionName = remember {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ComfyUI Workflow")
                        if (versionName.isNotBlank()) {
                            Text(
                                text = "v$versionName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        val galleryEnabled = allOutputs.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(
                                    enabled = galleryEnabled,
                                    onClick = onOpenGallery
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                modifier = Modifier.alpha(if (galleryEnabled) 1f else 0.38f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(onClick = onOpenSchemaGenerator),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Schema generator"
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { showSettings = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { showLogView = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.List,
                                contentDescription = "Logs"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilePickerSection(
                workflowName = uiState.workflowName,
                schemaName = uiState.schemaName,
                onWorkflowPicked = viewModel::loadWorkflow,
                onSchemaPicked = viewModel::loadSchema,
                onSaveSchemaDefaults = viewModel::saveSchemaDefaults
            )

            if (uiState.hasWorkflow && uiState.hasSchema && uiState.parameters.isNotEmpty()) {
                GenerationPanel(
                    status = uiState.generationStatus,
                    batchCount = uiState.batchCount,
                    onBatchCountChange = viewModel::onBatchCountChange,
                    onGenerateClick = viewModel::generate,
                    onCancelClick = viewModel::cancelGeneration
                )
            }

            if (uiState.preview != null) {
                PreviewPanel(preview = uiState.preview)
            }

            if (uiState.parameters.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { parametersExpanded = !parametersExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Parameters",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { parametersExpanded = !parametersExpanded }) {
                        Icon(
                            imageVector = if (parametersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (parametersExpanded) "Collapse parameters" else "Expand parameters"
                        )
                    }
                }

                AnimatedVisibility(visible = parametersExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.parameters
                            .groupBy { it.nodeId }
                            .forEach { (nodeId, params) ->
                                val nodeLabel = params.first().nodeLabel
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = nodeLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    params.forEach { param ->
                                        val key = ParameterKey(param.nodeId, param.path)
                                        ParameterEditor(
                                            param = param,
                                            value = uiState.currentValues[key] ?: param.default
                                                ?: if (param.type is FieldType.StringType || param.type is FieldType.OptionType) {
                                                    JsonPrimitive("")
                                                } else {
                                                    JsonPrimitive(0)
                                                },
                                            pendingUploadUri = uiState.pendingUploads[key],
                                            options = uiState.optionLists[key] ?: emptyList(),
                                            isLoading = uiState.optionLoading.contains(key),
                                            isFixedSeed = uiState.fixedSeeds.contains(key),
                                            isModified = uiState.modifiedKeys.contains(key),
                                            onValueChange = { viewModel.updateValue(param, it) },
                                            onUploadUriSelected = { viewModel.setUploadUri(param, it) },
                                            onLoadOptions = { viewModel.loadOptions(param) },
                                            onRefreshOptions = { viewModel.refreshOptions(param) },
                                            onToggleFixedSeed = { viewModel.toggleFixedSeed(param) }
                                        )
                                    }
                                }
                            }
                    }
                }
            }

            if (uiState.hasWorkflow && uiState.hasSchema && uiState.parameters.isEmpty()) {
                Text(
                    text = "No matching parameters found. The schema may not belong to this workflow.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showLogView) {
        LogViewDialog(onDismiss = { showLogView = false })
    }

    if (showSettings) {
        SettingsDialog(
            currentUrl = uiState.serverUrl,
            urlHistory = uiState.serverUrlHistory,
            currentTimeoutMs = uiState.generationTimeoutMs,
            onSave = { url, timeoutMs ->
                viewModel.onServerUrlChange(url)
                viewModel.saveServerUrl()
                viewModel.onGenerationTimeoutChange(timeoutMs)
                viewModel.saveGenerationTimeout()
            },
            onDismiss = { showSettings = false },
            onHistoryClick = { viewModel.onServerUrlChange(it) }
        )
    }
}

@Composable
private fun ParameterEditor(
    param: EditableParameter,
    value: kotlinx.serialization.json.JsonElement,
    pendingUploadUri: android.net.Uri?,
    options: List<String>,
    isLoading: Boolean,
    isFixedSeed: Boolean,
    isModified: Boolean,
    onValueChange: (kotlinx.serialization.json.JsonElement) -> Unit,
    onUploadUriSelected: (android.net.Uri) -> Unit,
    onLoadOptions: () -> Unit,
    onRefreshOptions: () -> Unit,
    onToggleFixedSeed: () -> Unit
) {
    val label = param.fieldName

    if (param.type is FieldType.OptionType) {
        LaunchedEffect(param) {
            onLoadOptions()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isModified) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }

        when (val type = param.type) {
            is FieldType.StringType -> {
                StringFieldEditor(
                    label = label,
                    value = value.asString(),
                    onValueChange = { onValueChange(JsonPrimitive(it)) },
                    singleLine = !param.multiline,
                    modifier = Modifier.weight(1f)
                )
            }
            is FieldType.IntType -> {
                IntFieldEditor(
                    label = label,
                    value = value.asNumber(),
                    min = param.min,
                    max = param.max,
                    precision = param.precision,
                    onValueChange = { onValueChange(it.toJsonPrimitive(param.precision)) },
                    modifier = Modifier.weight(1f)
                )
            }
            is FieldType.SeedType -> {
                SeedFieldEditor(
                    label = label,
                    value = value.asLong(),
                    fixed = isFixedSeed,
                    onValueChange = { onValueChange(JsonPrimitive(it)) },
                    onFixedChange = { onToggleFixedSeed() },
                    modifier = Modifier.weight(1f)
                )
            }
            is FieldType.DimensionType -> {
                IntFieldEditor(
                    label = label,
                    value = value.asNumber(),
                    min = param.min,
                    max = param.max,
                    precision = 0,
                    onValueChange = { onValueChange(it.toJsonPrimitive(0)) },
                    modifier = Modifier.weight(1f)
                )
            }
            is FieldType.UploadType -> {
                UploadFieldEditor(
                    label = label,
                    mimeType = type.mimeType,
                    selectedUri = pendingUploadUri,
                    onUriSelected = onUploadUriSelected,
                    modifier = Modifier.weight(1f)
                )
            }
            is FieldType.OptionType -> {
                OptionFieldEditor(
                    label = label,
                    options = options,
                    selected = value.asString(),
                    onValueChange = { onValueChange(JsonPrimitive(it)) },
                    onRefresh = onRefreshOptions,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
