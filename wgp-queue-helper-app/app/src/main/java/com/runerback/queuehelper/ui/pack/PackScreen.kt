@file:OptIn(ExperimentalLayoutApi::class)

package com.runerback.queuehelper.ui.pack

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
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
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.runerback.queuehelper.QueueHelperApplication
import kotlinx.coroutines.launch
import com.runerback.queuehelper.data.model.DescriptionSegment
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.parseDescriptionSegments
import com.runerback.queuehelper.domain.PackAllUseCase
import com.runerback.queuehelper.ui.icons.PhosphorPackage

private val Resolutions = listOf("480x832", "832x480")
private const val MAX_PICTURE_SLOTS = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackScreen(
    presetId: Int,
    onEditTask: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QueueHelperApplication
    val viewModel: PackViewModel = viewModel(
        factory = PackViewModel.Factory(
            presetId,
            app.presetRepository,
            app.taskRepository,
            app.templateLoader
        )
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val packAllUseCase = remember {
        PackAllUseCase(context, app.taskRepository, presetId)
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                val result = packAllUseCase()
                snackbarHostState.showSnackbar(result)
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Storage permission required to save to Downloads")
            }
        }
    }

    val doPackAll: () -> Unit = {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch {
                val result = packAllUseCase()
                snackbarHostState.showSnackbar(result)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks - ${viewModel.presetName}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.createTaskFromPreset() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create task"
                        )
                    }
                    if (viewModel.tasks.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllTasks() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear all tasks"
                            )
                        }
                    }
                    IconButton(
                        onClick = doPackAll,
                        enabled = viewModel.tasks.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = PhosphorPackage,
                            contentDescription = "Pack queue.zip"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            itemsIndexed(viewModel.tasks, key = { _, task -> task.id }) { index, task ->
                TaskItem(
                    index = index,
                    presetName = viewModel.presetName,
                    onEdit = { onEditTask(task.id) },
                    onDelete = { viewModel.deleteTaskAndRenumber(task.id) }
                )
            }
        }
    }
}

@Composable
private fun TaskItem(
    index: Int,
    presetName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Task ${index + 1}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = presetName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit task"
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete task"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(
    taskId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QueueHelperApplication
    val viewModel: TaskEditorViewModel = viewModel(
        factory = TaskEditorViewModel.Factory(
            context,
            app.taskRepository,
            app.templateLoader
        )
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingSubject by remember { mutableStateOf<SubjectDefinition?>(null) }
    val dialogSubject by remember {
        derivedStateOf {
            editingSubject?.let { current ->
                viewModel.subjects.find { it.id == current.id }
            }
        }
    }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var tokenToChange by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(viewModel.packResult) {
        viewModel.packResult?.let { result ->
            snackbarHostState.showSnackbar(result)
            viewModel.dismissResult()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setAudio(it) }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.pack()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Storage permission required to save to Downloads")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pack Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                Text(
                    text = "Prompt Sections",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Subject definitions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(
                        onClick = {
                            editingSubject = null
                            showSubjectDialog = true
                        }
                    ) {
                        Text("Add Subject")
                    }
                }
            }

            items(viewModel.subjects, key = { it.id }) { subject ->
                SubjectDefinitionCard(
                    subject = subject,
                    imageUris = viewModel.imageUris,
                    onEdit = {
                        editingSubject = subject
                        showSubjectDialog = true
                    },
                    onRemove = { viewModel.removeSubject(subject.id) },
                    onPictureClick = { number ->
                        tokenToChange = subject.id to number
                    },
                    onPictureDelete = { segmentIndex ->
                        viewModel.removeSubjectPictureToken(subject.id, segmentIndex)
                    }
                )
            }

            item {
                AudioDefinitionControl(
                    audioUri = viewModel.audioUri,
                    audioDefinitionLine = viewModel.audioDefinitionLine,
                    onAdd = { viewModel.addAudioDefinition() },
                    onRemove = { viewModel.removeAudioDefinition() }
                )
            }

            item {
                InlineTokenEditor(
                    value = viewModel.prompt.summary,
                    onValueChange = {
                        viewModel.updatePrompt(viewModel.prompt.copy(summary = it))
                    },
                    subjects = viewModel.subjects,
                    imageUris = viewModel.imageUris,
                    label = { Text("summary") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }

            item {
                InlineTokenEditor(
                    value = viewModel.prompt.retentionAnalysis,
                    onValueChange = {
                        viewModel.updatePrompt(viewModel.prompt.copy(retentionAnalysis = it))
                    },
                    subjects = viewModel.subjects,
                    imageUris = viewModel.imageUris,
                    label = { Text("retention_analysis") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }

            item {
                InlineTokenEditor(
                    value = viewModel.prompt.detailedDescription,
                    onValueChange = {
                        viewModel.updatePrompt(viewModel.prompt.copy(detailedDescription = it))
                    },
                    subjects = viewModel.subjects,
                    imageUris = viewModel.imageUris,
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
                    subjects = viewModel.subjects,
                    imageUris = viewModel.imageUris,
                    label = { Text("non_diegetic_music") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }

            item {
                PackResolutionDropdown(
                    selected = viewModel.resolution,
                    onSelected = { viewModel.updateResolution(it) }
                )
            }

            item {
                Text(
                    text = "Images (${viewModel.imageUris.size}/6)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.imageUris.size < 6
                ) {
                    Text("Select Images")
                }
            }

            items(viewModel.imageUris.size) { index ->
                ImageListItem(
                    index = index,
                    uri = viewModel.imageUris[index],
                    onRemove = { viewModel.removeImage(index) }
                )
            }

            item {
                Text(
                    text = "Audio",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                OutlinedButton(
                    onClick = { audioPicker.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Audio")
                }
            }

            item {
                viewModel.audioUri?.let { uri ->
                    AudioInfoCard(
                        uri = uri,
                        duration = viewModel.audioDurationSeconds,
                        trimStart = viewModel.trimStart,
                        trimEnd = viewModel.trimEnd,
                        onTrimStartChange = { viewModel.updateTrimStart(it) },
                        onTrimEndChange = { viewModel.updateTrimEnd(it) }
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "video_length",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = viewModel.computedVideoLength().toString(),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showSubjectDialog) {
        SubjectEditorDialog(
            subject = dialogSubject,
            imageUris = viewModel.imageUris,
            onDismiss = { showSubjectDialog = false },
            onConfirm = { description ->
                val current = editingSubject
                if (current == null) {
                    viewModel.addSubject(description)
                } else {
                    viewModel.updateSubject(current.id, description)
                }
                showSubjectDialog = false
            }
        )
    }

    tokenToChange?.let { (subjectId, currentNumber) ->
        PicturePickerDialog(
            currentNumber = currentNumber,
            imageUris = viewModel.imageUris,
            onDismiss = { tokenToChange = null },
            onSelected = { newNumber ->
                viewModel.replaceSubjectPictureToken(subjectId, currentNumber, newNumber)
                tokenToChange = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackResolutionDropdown(
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

@Composable
private fun SubjectDefinitionCard(
    subject: SubjectDefinition,
    imageUris: List<Uri>,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onPictureClick: (Int) -> Unit,
    onPictureDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Subject ${subject.number}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit subject"
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove subject"
                    )
                }
            }

            InlineDescription(
                description = subject.description,
                imageUris = imageUris,
                onPictureClick = onPictureClick,
                onPictureDelete = onPictureDelete
            )
        }
    }
}

@Composable
fun InlineDescription(
    description: String,
    imageUris: List<Uri>,
    onPictureClick: (Int) -> Unit,
    onPictureDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(description) { parseDescriptionSegments(description) }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
        verticalArrangement = Arrangement.Center
    ) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is DescriptionSegment.Text -> {
                    Text(
                        text = segment.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is DescriptionSegment.Picture -> {
                    PictureTokenChip(
                        number = segment.number,
                        imageUri = imageUris.getOrNull(segment.number - 1),
                        onClick = { onPictureClick(segment.number) },
                        onDelete = { onPictureDelete(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun PicturePickerDialog(
    currentNumber: Int,
    imageUris: List<Uri>,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "Choose picture",
                    style = MaterialTheme.typography.titleMedium
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(MAX_PICTURE_SLOTS) { index ->
                        val number = index + 1
                        val uri = imageUris.getOrNull(index)
                        val selected = number == currentNumber

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable { onSelected(number) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (uri != null) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Picture $number",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = "P$number",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioDefinitionControl(
    audioUri: Uri?,
    audioDefinitionLine: String?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Audio definition",
            style = MaterialTheme.typography.titleSmall
        )

        when {
            audioUri == null -> {
                Text(
                    text = "Upload an audio file to enable the audio definition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            audioDefinitionLine == null -> {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Audio")
                }
            }

            else -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = audioDefinitionLine,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                        IconButton(onClick = onRemove) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove audio definition"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageListItem(
    index: Int,
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Image ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Image ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove"
                )
            }
        }
    }
}

@Composable
private fun AudioInfoCard(
    uri: Uri,
    duration: Float,
    trimStart: Float,
    trimEnd: Float,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Selected: ${uri.lastPathSegment ?: uri.toString()}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                text = "Duration: %.1f s".format(duration),
                style = MaterialTheme.typography.bodySmall
            )
            if (duration > 0f) {
                val range = trimStart..trimEnd
                RangeSlider(
                    value = range,
                    onValueChange = {
                        onTrimStartChange(it.start)
                        onTrimEndChange(it.endInclusive)
                    },
                    valueRange = 0f..duration.coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start: %.1f s".format(trimStart))
                    Text("End: %.1f s".format(trimEnd))
                }
            }
        }
    }
}
