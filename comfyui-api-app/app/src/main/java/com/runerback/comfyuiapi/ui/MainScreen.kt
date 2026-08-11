package com.runerback.comfyuiapi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.comfyuiapi.data.model.EditableParameter
import com.runerback.comfyuiapi.data.model.FieldType
import com.runerback.comfyuiapi.data.model.ParameterKey
import com.runerback.comfyuiapi.ui.components.FilePickerSection
import com.runerback.comfyuiapi.ui.components.GalleryPanel
import com.runerback.comfyuiapi.ui.components.GenerationPanel
import com.runerback.comfyuiapi.ui.components.IntFieldEditor
import com.runerback.comfyuiapi.ui.components.LogViewDialog
import com.runerback.comfyuiapi.ui.components.PreviewPanel
import com.runerback.comfyuiapi.ui.components.SeedFieldEditor
import com.runerback.comfyuiapi.ui.components.SettingsDialog
import com.runerback.comfyuiapi.ui.components.StringFieldEditor
import com.runerback.comfyuiapi.ui.components.UploadFieldEditor
import com.runerback.comfyuiapi.ui.components.asLong
import com.runerback.comfyuiapi.ui.components.asString
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSchemaGenerator: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogView by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ComfyUI Workflow") },
                actions = {
                    IconButton(onClick = onOpenSchemaGenerator) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Schema generator"
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(onClick = { showLogView = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.List,
                            contentDescription = "Logs"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilePickerSection(
                workflowName = uiState.workflowName,
                schemaName = uiState.schemaName,
                onWorkflowPicked = viewModel::loadWorkflow,
                onSchemaPicked = viewModel::loadSchema
            )

            if (uiState.parameters.isNotEmpty()) {
                Text(
                    text = "Parameters",
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = viewModel::randomizeSeeds,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Randomize all seeds")
                }

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
                                ParameterEditor(
                                    param = param,
                                    value = uiState.currentValues[ParameterKey(param.nodeId, param.path)]
                                        ?: param.default ?: JsonPrimitive(0),
                                    pendingUploadUri = uiState.pendingUploads[ParameterKey(param.nodeId, param.path)],
                                    onValueChange = { viewModel.updateValue(param, it) },
                                    onUploadUriSelected = { viewModel.setUploadUri(param, it) }
                                )
                            }
                        }
                    }
            }

            GenerationPanel(
                status = uiState.generationStatus,
                onGenerateClick = viewModel::generate
            )

            PreviewPanel(preview = uiState.preview)

            GalleryPanel(images = uiState.outputs)

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
    onValueChange: (kotlinx.serialization.json.JsonElement) -> Unit,
    onUploadUriSelected: (android.net.Uri) -> Unit
) {
    val label = param.fieldName

    when (val type = param.type) {
        is FieldType.StringType -> {
            StringFieldEditor(
                label = label,
                value = value.asString(),
                onValueChange = { onValueChange(JsonPrimitive(it)) },
                singleLine = label.lowercase().contains("prompt").not()
            )
        }
        is FieldType.IntType -> {
            IntFieldEditor(
                label = label,
                value = value.asLong(),
                min = param.min,
                max = param.max,
                onValueChange = { onValueChange(JsonPrimitive(it)) }
            )
        }
        is FieldType.SeedType -> {
            SeedFieldEditor(
                label = label,
                value = value.asLong(),
                onValueChange = { onValueChange(JsonPrimitive(it)) }
            )
        }
        is FieldType.DimensionType -> {
            IntFieldEditor(
                label = label,
                value = value.asLong(),
                min = param.min,
                max = param.max,
                onValueChange = { onValueChange(JsonPrimitive(it)) }
            )
        }
        is FieldType.UploadType -> {
            UploadFieldEditor(
                label = label,
                mimeType = type.mimeType,
                selectedUri = pendingUploadUri,
                onUriSelected = onUploadUriSelected
            )
        }
    }
}
