@file:OptIn(ExperimentalLayoutApi::class)

package com.runerback.queuehelper.ui.pack

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.runerback.queuehelper.QueueHelperApplication
import kotlinx.coroutines.launch
import com.runerback.queuehelper.data.local.MediaRepository
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.Task
import com.runerback.queuehelper.data.model.Token
import com.runerback.queuehelper.domain.PackAllUseCase
import com.runerback.queuehelper.ui.components.LoadingIndicator
import com.runerback.queuehelper.ui.icons.PhosphorPackage
import com.runerback.queuehelper.ui.common.CollapsibleSection
import com.runerback.queuehelper.ui.common.ResolutionDropdown
import com.runerback.queuehelper.ui.common.SubjectCard
import com.runerback.queuehelper.ui.common.TokenFieldAvailability
import com.runerback.queuehelper.ui.common.TokenInputToolbar
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_PICTURE_SLOTS = 6

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun PackScreen(
    presetId: Int?,
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadTasks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isPacking by remember { mutableStateOf(false) }
    val packAllUseCase = remember {
        PackAllUseCase(context, app.taskRepository, app.mediaRepository, presetId)
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                isPacking = true
                val result = packAllUseCase()
                isPacking = false
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
                isPacking = true
                val result = packAllUseCase()
                isPacking = false
                snackbarHostState.showSnackbar(result)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (presetId != null) "Tasks - ${viewModel.presetName}" else "Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.requestCreateTask() }) {
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
                        enabled = viewModel.tasks.isNotEmpty() && !isPacking
                    ) {
                        if (isPacking) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                contentDescription = "Packing"
                            )
                        } else {
                            Icon(
                                imageVector = PhosphorPackage,
                                contentDescription = "Pack queue.zip"
                            )
                        }
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
                val taskPresetName = if (presetId != null) {
                    viewModel.presetName
                } else {
                    viewModel.presetNameMap[task.presetId] ?: ""
                }
                val taskImageUris = rememberTaskImageUris(task, app.mediaRepository)
                val taskAudioUri = rememberTaskAudioUri(task, app.mediaRepository)
                TaskItem(
                    index = index,
                    presetName = taskPresetName,
                    audioUri = taskAudioUri,
                    imageUris = taskImageUris,
                    onEdit = { onEditTask(task.id) },
                    onDelete = { viewModel.deleteTaskAndRenumber(task.id) }
                )
            }
        }
    }

    if (viewModel.showPresetPicker) {
        PresetPickerDialog(
            presets = viewModel.presets,
            initialPresetId = viewModel.lastSelectedPresetId,
            onPresetSelected = { viewModel.createTaskFromPreset(it) },
            onDismiss = { viewModel.dismissPresetPicker() }
        )
    }
}

@Composable
private fun TaskItem(
    index: Int,
    presetName: String,
    audioUri: Uri?,
    imageUris: List<Uri>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (audioUri != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Audio selected",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                imageUris.take(2).forEach { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Image thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
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
            app.mediaRepository,
            app.templateLoader
        )
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var activeField by remember { mutableStateOf<Pair<String, TokenFieldAvailability>?>(null) }
    var pendingInsertToken by remember { mutableStateOf<Token?>(null) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            val focusManager = LocalFocusManager.current
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    }
            ) {
                item {
                    Text(
                        text = "Prompt Sections",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                item {
                    ResolutionDropdown(
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
                            maxDuration = viewModel.maxAudioDurationSeconds,
                            trimStart = viewModel.trimStart,
                            trimEnd = viewModel.trimEnd,
                            isPreviewPlaying = viewModel.isPreviewPlaying,
                            previewTrimmedOnly = viewModel.previewTrimmedOnly,
                            previewProgress = viewModel.previewProgress,
                            onTrimRangeChange = { start, end ->
                                viewModel.updateTrimRange(start, end)
                            },
                            onRemove = { viewModel.setAudio(null) },
                            onTogglePreview = { viewModel.togglePreview() },
                            onTogglePreviewMode = { viewModel.updatePreviewTrimmedOnly(it) }
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = viewModel.videoLengthInputText,
                        onValueChange = { text ->
                            viewModel.updateVideoLengthInputText(text)
                        },
                        label = { Text("video_length") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            val parsed = viewModel.videoLengthInputText.toIntOrNull()
                            if (parsed == null || parsed <= 0) {
                                Text("Using calculated value: ${viewModel.effectiveVideoLength()}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        text = "Subject definitions",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(viewModel.subjects, key = { it.id }) { subject ->
                    val fieldId = "subject_${subject.id}"
                    SubjectCard(
                        number = subject.number,
                        description = subject.description,
                        imageUris = viewModel.imageUris,
                        onRemove = { viewModel.removeSubject(subject.id) },
                        onUpdateDescription = { viewModel.updateSubject(subject.id, it) },
                        fieldId = fieldId,
                        onFocusChanged = { focused, availability ->
                            activeField = if (focused) fieldId to availability else null
                        },
                        pendingInsertToken = if (activeField?.first == fieldId) pendingInsertToken else null,
                        onTokenInserted = { pendingInsertToken = null }
                    )
                }

                item {
                    CollapsibleSection(
                        title = "Audio definition",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AudioDefinitionControl(
                            audioUri = viewModel.audioUri,
                            audioDefinitionLine = viewModel.audioDefinitionLine,
                            onRemove = { viewModel.removeAudioDefinition() }
                        )
                    }
                }

                item {
                    CollapsibleSection(
                        title = "summary",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InlineTokenEditor(
                            value = viewModel.prompt.summary,
                            onValueChange = {
                                viewModel.updatePrompt(viewModel.prompt.copy(summary = it))
                            },
                            subjects = viewModel.subjects,
                            imageUris = viewModel.imageUris,
                            label = {},
                            fieldId = "summary",
                            onFocusChanged = { focused, availability ->
                                activeField = if (focused) "summary" to availability else null
                            },
                            pendingInsertToken = if (activeField?.first == "summary") pendingInsertToken else null,
                            onTokenInserted = { pendingInsertToken = null },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                    }
                }

                item {
                    CollapsibleSection(
                        title = "retention_analysis",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InlineTokenEditor(
                            value = viewModel.prompt.retentionAnalysis,
                            onValueChange = {
                                viewModel.updatePrompt(viewModel.prompt.copy(retentionAnalysis = it))
                            },
                            subjects = viewModel.subjects,
                            imageUris = viewModel.imageUris,
                            label = {},
                            fieldId = "retention_analysis",
                            onFocusChanged = { focused, availability ->
                                activeField = if (focused) "retention_analysis" to availability else null
                            },
                            pendingInsertToken = if (activeField?.first == "retention_analysis") pendingInsertToken else null,
                            onTokenInserted = { pendingInsertToken = null },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                    }
                }

                item {
                    CollapsibleSection(
                        title = "detailed_description",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InlineTokenEditor(
                            value = viewModel.prompt.detailedDescription,
                            onValueChange = {
                                viewModel.updatePrompt(viewModel.prompt.copy(detailedDescription = it))
                            },
                            subjects = viewModel.subjects,
                            imageUris = viewModel.imageUris,
                            label = {},
                            fieldId = "detailed_description",
                            onFocusChanged = { focused, availability ->
                                activeField = if (focused) "detailed_description" to availability else null
                            },
                            pendingInsertToken = if (activeField?.first == "detailed_description") pendingInsertToken else null,
                            onTokenInserted = { pendingInsertToken = null },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8
                        )
                    }
                }

                item {
                    CollapsibleSection(
                        title = "non_diegetic_music",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InlineTokenEditor(
                            value = viewModel.prompt.nonDiegeticMusic,
                            onValueChange = {
                                viewModel.updatePrompt(viewModel.prompt.copy(nonDiegeticMusic = it))
                            },
                            subjects = viewModel.subjects,
                            imageUris = viewModel.imageUris,
                            label = {},
                            fieldId = "non_diegetic_music",
                            onFocusChanged = { focused, availability ->
                                activeField = if (focused) "non_diegetic_music" to availability else null
                            },
                            pendingInsertToken = if (activeField?.first == "non_diegetic_music") pendingInsertToken else null,
                            onTokenInserted = { pendingInsertToken = null },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (activeField != null && imeVisible) {
                Spacer(modifier = Modifier.height(8.dp))
                TokenInputToolbar(
                    onInsert = { pendingInsertToken = it },
                    availability = TokenFieldAvailability(true, true, true),
                    modifier = Modifier.fillMaxWidth()
                )
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
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        when {
            audioUri == null -> {
                Text(
                    text = "Upload an audio file to enable the audio definition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            audioDefinitionLine == null -> {
                Text(
                    text = "No audio definition available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun rememberTaskImageUris(task: Task, mediaRepository: MediaRepository): List<Uri> {
    return produceState<List<Uri>>(initialValue = emptyList(), task) {
        val packSettings = task.payload["pack_settings"]?.jsonObject
        val mediaIds = packSettings?.get("image_media_ids")?.jsonArray?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull
        }
        value = if (mediaIds != null) {
            mediaRepository.resolveIds(mediaIds).map { it.uri }
        } else {
            packSettings?.get("image_uris")?.jsonArray?.mapNotNull { element ->
                element.jsonPrimitive.contentOrNull?.let { uriString ->
                    runCatching { Uri.parse(uriString) }.getOrNull()
                }
            } ?: emptyList()
        }
    }.value
}

@Composable
private fun rememberTaskAudioUri(task: Task, mediaRepository: MediaRepository): Uri? {
    return produceState<Uri?>(initialValue = null, task) {
        val packSettings = task.payload["pack_settings"]?.jsonObject
        val mediaId = packSettings?.get("audio_media_id")?.jsonPrimitive?.contentOrNull
        value = if (mediaId != null) {
            mediaRepository.get(mediaId)?.uri
        } else {
            packSettings?.get("audio_uri")?.jsonPrimitive?.contentOrNull?.let { uriString ->
                runCatching { Uri.parse(uriString) }.getOrNull()
            }
        }
    }.value
}

@Composable
private fun AudioInfoCard(
    uri: Uri,
    duration: Float,
    maxDuration: Float,
    trimStart: Float,
    trimEnd: Float,
    isPreviewPlaying: Boolean,
    previewTrimmedOnly: Boolean,
    previewProgress: Float,
    onTrimRangeChange: (Float, Float) -> Unit,
    onRemove: () -> Unit,
    onTogglePreview: () -> Unit,
    onTogglePreviewMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var previousRange by remember(duration, maxDuration) { mutableStateOf(trimStart..trimEnd) }

    LaunchedEffect(trimStart, trimEnd) {
        previousRange = trimStart..trimEnd
    }

    val exceedsMax = maxDuration.isFinite() && duration > maxDuration

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Selected: ${uri.lastPathSegment ?: uri.toString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove audio"
                    )
                }
            }
            Text(
                text = "Duration: %.1f s".format(duration),
                style = MaterialTheme.typography.bodySmall
            )
            if (exceedsMax) {
                Text(
                    text = "Source exceeds max allowed duration of %.1f s; selected range is limited to %.1f s.".format(maxDuration, maxDuration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (duration > 0f) {
                RangeSlider(
                    value = trimStart..trimEnd,
                    onValueChange = { range ->
                        val (newStart, newEnd) = computePannedRange(
                            previousRange = previousRange,
                            newRange = range,
                            maxWindow = if (maxDuration.isFinite()) maxDuration else Float.MAX_VALUE,
                            duration = duration
                        )
                        onTrimRangeChange(newStart, newEnd)
                        previousRange = newStart..newEnd
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onTogglePreview) {
                        Icon(
                            imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewPlaying) "Stop preview" else "Play preview"
                        )
                    }
                    LinearProgressIndicator(
                        progress = { previewProgress },
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Trimmed only",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = previewTrimmedOnly,
                            onCheckedChange = onTogglePreviewMode
                        )
                    }
                }
            }
        }
    }
}

private fun computePannedRange(
    previousRange: ClosedFloatingPointRange<Float>,
    newRange: ClosedFloatingPointRange<Float>,
    maxWindow: Float,
    duration: Float
): Pair<Float, Float> {
    var newStart = newRange.start.coerceIn(0f, duration)
    var newEnd = newRange.endInclusive.coerceIn(0f, duration)

    if (maxWindow.isFinite() && newEnd - newStart > maxWindow) {
        val startDelta = newStart - previousRange.start
        val endDelta = newEnd - previousRange.endInclusive

        if (kotlin.math.abs(endDelta) >= kotlin.math.abs(startDelta)) {
            newEnd = newEnd.coerceAtMost(duration)
            newStart = (newEnd - maxWindow).coerceAtLeast(0f)
        } else {
            newStart = newStart.coerceAtLeast(0f)
            newEnd = (newStart + maxWindow).coerceAtMost(duration)
        }
    }

    return newStart to newEnd
}
