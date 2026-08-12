package com.runerback.comfyuiapi.ui.schemagenerator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.comfyuiapi.data.model.SchemaFieldRole
import com.runerback.comfyuiapi.data.model.SchemaFieldSelection
import com.runerback.comfyuiapi.data.model.SchemaFieldType
import com.runerback.comfyuiapi.domain.OPTION_KIND_SOURCES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemaGeneratorScreen(
    viewModel: SchemaGeneratorViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val workflowLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { viewModel.loadWorkflow(it) } }
    )
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let { viewModel.exportSchema(it) } }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schema Generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            Button(
                onClick = { workflowLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pick workflow JSON")
            }

            if (uiState.workflowName.isNotBlank()) {
                Text(
                    text = "Workflow: ${uiState.workflowName}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.selections.isNotEmpty()) {
                Text(
                    text = "Select fields to expose",
                    style = MaterialTheme.typography.titleMedium
                )

                val grouped = uiState.selections.groupBy { it.nodeId }
                grouped.forEach { (nodeId, selections) ->
                    NodeSection(
                        nodeId = nodeId,
                        nodeLabel = selections.first().nodeLabel,
                        selections = selections,
                        onToggle = { viewModel.toggleSelection(nodeId, it) },
                        onTypeChange = { field, type -> viewModel.updateType(nodeId, field, type) },
                        onRoleChange = { field, role -> viewModel.updateRole(nodeId, field, role) },
                        onUploadTypeChange = { field, value -> viewModel.updateUploadType(nodeId, field, value) },
                        onMimeTypeChange = { field, value -> viewModel.updateMimeType(nodeId, field, value) },
                        onMinChange = { field, value -> viewModel.updateMin(nodeId, field, value) },
                        onMaxChange = { field, value -> viewModel.updateMax(nodeId, field, value) },
                        onOrderChange = { field, value -> viewModel.updateOrder(nodeId, field, value) },
                        onMultilineChange = { field, value -> viewModel.updateMultiline(nodeId, field, value) },
                        onOptionKindChange = { field, value -> viewModel.updateOptionKind(nodeId, field, value) },
                        onPrecisionChange = { field, value -> viewModel.updatePrecision(nodeId, field, value) }
                    )
                }

                val selectedCount = uiState.selections.count { it.selected }
                Button(
                    onClick = { exportLauncher.launch("${uiState.workflowName.substringBeforeLast(".")}_generated.schema.json") },
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export schema ($selectedCount selected)")
                }
            } else if (uiState.workflow == null && uiState.errorMessage == null) {
                Text(
                    text = "Pick a workflow JSON to start building its schema.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.exportedUri?.let { uri ->
                ExportedBanner(uri = uri, onDismiss = viewModel::dismissExported)
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ExportedBanner(
    uri: Uri,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Schema exported",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = uri.lastPathSegment ?: uri.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun NodeSection(
    nodeId: String,
    nodeLabel: String,
    selections: List<SchemaFieldSelection>,
    onToggle: (String) -> Unit,
    onTypeChange: (String, SchemaFieldType) -> Unit,
    onRoleChange: (String, SchemaFieldRole) -> Unit,
    onUploadTypeChange: (String, String) -> Unit,
    onMimeTypeChange: (String, String) -> Unit,
    onMinChange: (String, Long?) -> Unit,
    onMaxChange: (String, Long?) -> Unit,
    onOrderChange: (String, Int) -> Unit,
    onMultilineChange: (String, Boolean) -> Unit,
    onOptionKindChange: (String, String) -> Unit,
    onPrecisionChange: (String, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nodeLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = nodeId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    selections.forEach { selection ->
                        FieldEditor(
                            selection = selection,
                            onToggle = { onToggle(selection.fieldName) },
                            onTypeChange = { onTypeChange(selection.fieldName, it) },
                            onRoleChange = { onRoleChange(selection.fieldName, it) },
                            onUploadTypeChange = { onUploadTypeChange(selection.fieldName, it) },
                            onMimeTypeChange = { onMimeTypeChange(selection.fieldName, it) },
                            onMinChange = { onMinChange(selection.fieldName, it) },
                            onMaxChange = { onMaxChange(selection.fieldName, it) },
                            onOrderChange = { onOrderChange(selection.fieldName, it) },
                            onMultilineChange = { onMultilineChange(selection.fieldName, it) },
                            onOptionKindChange = { onOptionKindChange(selection.fieldName, it) },
                            onPrecisionChange = { onPrecisionChange(selection.fieldName, it) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditor(
    selection: SchemaFieldSelection,
    onToggle: () -> Unit,
    onTypeChange: (SchemaFieldType) -> Unit,
    onRoleChange: (SchemaFieldRole) -> Unit,
    onUploadTypeChange: (String) -> Unit,
    onMimeTypeChange: (String) -> Unit,
    onMinChange: (Long?) -> Unit,
    onMaxChange: (Long?) -> Unit,
    onOrderChange: (Int) -> Unit,
    onMultilineChange: (Boolean) -> Unit,
    onOptionKindChange: (String) -> Unit,
    onPrecisionChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selection.selected,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selection.fieldName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = selection.currentValue.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (selection.selected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownSelector(
                    label = "Type",
                    options = SchemaFieldType.entries.map { it.value },
                    selected = selection.type.value,
                    onSelected = { value ->
                        SchemaFieldType.entries.find { it.value == value }?.let(onTypeChange)
                    },
                    modifier = Modifier.weight(1f)
                )
                DropdownSelector(
                    label = "Role",
                    options = SchemaFieldRole.entries.map { it.value ?: "none" },
                    selected = selection.role.value ?: "none",
                    onSelected = { value ->
                        SchemaFieldRole.entries.find { it.value == value || (value == "none" && it.value == null) }?.let(onRoleChange)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selection.type == SchemaFieldType.String) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selection.multiline,
                        onCheckedChange = onMultilineChange
                    )
                    Text(
                        text = "Multiline",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (selection.role == SchemaFieldRole.Upload) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = selection.uploadType,
                        onValueChange = onUploadTypeChange,
                        label = { Text("Upload type") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = selection.mimeType,
                        onValueChange = onMimeTypeChange,
                        label = { Text("MIME type") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (selection.role == SchemaFieldRole.Option) {
                DropdownSelector(
                    label = "Option kind",
                    options = OPTION_KIND_SOURCES.keys.toList().sorted(),
                    selected = selection.optionKind,
                    onSelected = onOptionKindChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 8.dp)
                )
            }

            if (selection.type == SchemaFieldType.Integer || selection.type == SchemaFieldType.Number) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionalLongField(
                        label = "Min",
                        value = selection.min,
                        onValueChange = onMinChange,
                        modifier = Modifier.weight(1f)
                    )
                    OptionalLongField(
                        label = "Max",
                        value = selection.max,
                        onValueChange = onMaxChange,
                        modifier = Modifier.weight(1f)
                    )
                }

                DropdownSelector(
                    label = "Precision",
                    options = listOf("0", "1", "2"),
                    selected = selection.precision.toString(),
                    onSelected = { onPrecisionChange(it.toIntOrNull() ?: 0) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 8.dp)
                )
            }

            OutlinedTextField(
                value = selection.order.toString(),
                onValueChange = { onOrderChange(it.toIntOrNull() ?: 0) },
                label = { Text("Order") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionalLongField(
    label: String,
    value: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it.toLongOrNull())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
