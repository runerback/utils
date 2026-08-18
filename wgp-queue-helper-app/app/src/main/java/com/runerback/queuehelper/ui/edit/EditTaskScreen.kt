package com.runerback.queuehelper.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.queuehelper.QueueHelperApplication
import com.runerback.queuehelper.data.model.SubjectDefault
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.ui.pack.InlineDescription
import com.runerback.queuehelper.ui.pack.InlineTokenEditor
import com.runerback.queuehelper.ui.pack.PicturePickerDialog
import com.runerback.queuehelper.ui.pack.TokenTextField

private val Resolutions = listOf("480x832", "832x480")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskScreen(
    taskId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QueueHelperApplication
    val viewModel: EditTaskViewModel = viewModel(
        factory = EditTaskViewModel.Factory(app.taskRepository, app.templateLoader)
    )

    val subjects = viewModel.subjectDefaults.map { SubjectDefinition(0, it.number, it.description) }

    var editingDefault by remember { mutableStateOf<SubjectDefault?>(null) }
    var showDefaultDialog by remember { mutableStateOf(false) }
    var pictureToChange by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.name.ifBlank { "Edit Task" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.save()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                ResolutionDropdown(
                    selected = viewModel.resolution,
                    onSelected = { viewModel.updateResolution(it) }
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Default Subject Definitions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(
                        onClick = {
                            editingDefault = null
                            showDefaultDialog = true
                        }
                    ) {
                        Text("Add Subject")
                    }
                }
            }

            items(viewModel.subjectDefaults, key = { it.number }) { default ->
                DefaultSubjectCard(
                    default = default,
                    onEdit = {
                        editingDefault = default
                        showDefaultDialog = true
                    },
                    onRemove = { viewModel.removeDefaultSubject(default.number) },
                    onPictureClick = { number ->
                        pictureToChange = default.number to number
                    },
                    onPictureDelete = { segmentIndex ->
                        viewModel.removeDefaultSubjectPicture(default.number, segmentIndex)
                    }
                )
            }

            item {
                InlineTokenEditor(
                    value = viewModel.audioDefault,
                    onValueChange = { viewModel.updateAudioDefault(it) },
                    subjects = emptyList(),
                    imageUris = emptyList(),
                    label = { Text("Default audio definition") },
                    availableSubjects = false,
                    availablePictures = false,
                    availableAudio = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 2
                )
            }

            item {
                Text(
                    text = "Prompt Sections",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                InlineTokenEditor(
                    value = viewModel.prompt.summary,
                    onValueChange = {
                        viewModel.updatePrompt(viewModel.prompt.copy(summary = it))
                    },
                    subjects = subjects,
                    imageUris = emptyList(),
                    label = { Text("summary") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }

            item {
                InlineTokenEditor(
                    value = viewModel.prompt.retentionAnalysis,
                    onValueChange = {
                        viewModel.updatePrompt(viewModel.prompt.copy(retentionAnalysis = it))
                    },
                    subjects = subjects,
                    imageUris = emptyList(),
                    label = { Text("retention_analysis") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }

            item {
                InlineTokenEditor(
                    value = viewModel.prompt.detailedDescription,
                    onValueChange = {
                        viewModel.updatePrompt(viewModel.prompt.copy(detailedDescription = it))
                    },
                    subjects = subjects,
                    imageUris = emptyList(),
                    label = { Text("detailed_description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )
            }

            item {
                InlineTokenEditor(
                    value = viewModel.prompt.nonDiegeticMusic,
                    onValueChange = {
                        viewModel.updatePrompt(viewModel.prompt.copy(nonDiegeticMusic = it))
                    },
                    subjects = subjects,
                    imageUris = emptyList(),
                    label = { Text("non_diegetic_music") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDefaultDialog) {
        DefaultSubjectDialog(
            default = editingDefault,
            onDismiss = { showDefaultDialog = false },
            onConfirm = { description ->
                val current = editingDefault
                if (current == null) {
                    viewModel.addDefaultSubject(description)
                } else {
                    viewModel.updateDefaultSubject(current.number, description)
                }
                showDefaultDialog = false
            }
        )
    }

    pictureToChange?.let { (subjectNumber, currentNumber) ->
        PicturePickerDialog(
            currentNumber = currentNumber,
            imageUris = emptyList(),
            onDismiss = { pictureToChange = null },
            onSelected = { newNumber ->
                viewModel.replaceDefaultSubjectPicture(subjectNumber, currentNumber, newNumber)
                pictureToChange = null
            }
        )
    }
}

@Composable
private fun DefaultSubjectCard(
    default: SubjectDefault,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onPictureClick: (Int) -> Unit,
    onPictureDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Subject ${default.number}",
                    style = MaterialTheme.typography.labelLarge
                )
                InlineDescription(
                    description = default.description,
                    imageUris = emptyList(),
                    onPictureClick = onPictureClick,
                    onPictureDelete = onPictureDelete
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit default subject"
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove default subject"
                )
            }
        }
    }
}

@Composable
private fun DefaultSubjectDialog(
    default: SubjectDefault?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var description by remember(default) { mutableStateOf(default?.description ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (default == null) "Add Default Subject" else "Edit Default Subject",
                    style = MaterialTheme.typography.titleMedium
                )

                InlineTokenEditor(
                    value = description,
                    onValueChange = { description = it },
                    subjects = emptyList(),
                    imageUris = emptyList(),
                    label = { Text("Description") },
                    availableSubjects = false,
                    availablePictures = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            onConfirm(description)
                            onDismiss()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolutionDropdown(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Resolution") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Resolutions.forEach { resolution ->
                DropdownMenuItem(
                    text = { Text(resolution) },
                    onClick = {
                        onSelected(resolution)
                        expanded = false
                    }
                )
            }
        }
    }
}
