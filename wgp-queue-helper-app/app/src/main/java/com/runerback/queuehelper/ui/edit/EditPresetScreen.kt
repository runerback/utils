package com.runerback.queuehelper.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.queuehelper.QueueHelperApplication
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.Token
import com.runerback.queuehelper.ui.pack.InlineTokenEditor
import com.runerback.queuehelper.ui.common.CollapsibleSection
import com.runerback.queuehelper.ui.common.ResolutionDropdown
import com.runerback.queuehelper.ui.common.SubjectCard
import com.runerback.queuehelper.ui.common.TokenFieldAvailability
import com.runerback.queuehelper.ui.common.TokenInputToolbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPresetScreen(
    presetId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as QueueHelperApplication
    val viewModel: EditPresetViewModel = viewModel(
        factory = EditPresetViewModel.Factory(app.presetRepository, app.templateLoader)
    )

    val subjects = viewModel.subjectDefaults.map { SubjectDefinition(0, it.number, it.description) }

    var activeField by remember { mutableStateOf<Pair<String, TokenFieldAvailability>?>(null) }
    var pendingInsertToken by remember { mutableStateOf<Token?>(null) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Preset - ${viewModel.name}") },
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
                        viewModel.save { onBack() }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                        onClick = { viewModel.addDefaultSubject("") }
                    ) {
                        Text("Add Subject")
                    }
                }
            }

            items(viewModel.subjectDefaults, key = { it.number }) { default ->
                val fieldId = "default_subject_${default.number}"
                SubjectCard(
                    number = default.number,
                    description = default.description,
                    onRemove = { viewModel.removeDefaultSubject(default.number) },
                    onUpdateDescription = { viewModel.updateDefaultSubject(default.number, it) },
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
                    title = "Default audio definition",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    InlineTokenEditor(
                        value = viewModel.audioDefault,
                        onValueChange = { viewModel.updateAudioDefault(it) },
                        subjects = emptyList(),
                        imageUris = emptyList(),
                        onFocusChanged = { focused, availability ->
                            activeField = if (focused) "audio_default" to availability else null
                        },
                        pendingInsertToken = if (activeField?.first == "audio_default") pendingInsertToken else null,
                        onTokenInserted = { pendingInsertToken = null },
                        availableSubjects = false,
                        availablePictures = false,
                        availableAudio = true,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 2
                    )
                }
            }

            item {
                Text(
                    text = "Prompt Sections",
                    style = MaterialTheme.typography.titleMedium
                )
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
                        subjects = subjects,
                        imageUris = emptyList(),
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
                        subjects = subjects,
                        imageUris = emptyList(),
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
                        subjects = subjects,
                        imageUris = emptyList(),
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
                    title = "overall_soundscape",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    InlineTokenEditor(
                        value = viewModel.prompt.overallSoundscape,
                        onValueChange = {
                            viewModel.updatePrompt(viewModel.prompt.copy(overallSoundscape = it))
                        },
                        subjects = subjects,
                        imageUris = emptyList(),
                        onFocusChanged = { focused, availability ->
                            activeField = if (focused) "overall_soundscape" to availability else null
                        },
                        pendingInsertToken = if (activeField?.first == "overall_soundscape") pendingInsertToken else null,
                        onTokenInserted = { pendingInsertToken = null },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
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
                        subjects = subjects,
                        imageUris = emptyList(),
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
