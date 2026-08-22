package com.runerback.ntfyclient.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.ntfyclient.R
import com.runerback.ntfyclient.data.local.Topic
import com.runerback.ntfyclient.data.local.db.MessageEntity
import com.runerback.ntfyclient.ui.components.LogViewDialog
import com.runerback.ntfyclient.ui.settings.SettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val receiveTopics by viewModel.receiveTopics.collectAsState()
    val sendTopics by viewModel.sendTopics.collectAsState()
    val latestMessages by viewModel.latestMessages.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var topicName by remember { mutableStateOf("") }
    var topicBeingEdited by remember { mutableStateOf<Topic?>(null) }
    var showLogView by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_topic)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showLogView = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.logs)
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                TopicTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    TopicTab.Receive -> stringResource(R.string.receive)
                                    TopicTab.Send -> stringResource(R.string.send)
                                }
                            )
                        }
                    )
                }
            }

            val topics = when (selectedTab) {
                TopicTab.Receive -> receiveTopics
                TopicTab.Send -> sendTopics
            }

            if (topics.isEmpty()) {
                EmptyTopics(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(topics, key = { it.name }) { topic ->
                        TopicItem(
                            topic = topic,
                            latestMessage = if (selectedTab == TopicTab.Receive) latestMessages[topic.name] else null,
                            showSettings = selectedTab == TopicTab.Receive,
                            onSettings = { topicBeingEdited = topic },
                            onDelete = { viewModel.removeTopic(topic) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_topic)) },
            text = {
                OutlinedTextField(
                    value = topicName,
                    onValueChange = { topicName = it },
                    label = { Text(stringResource(R.string.topic_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTopic(topicName)
                        topicName = ""
                        showAddDialog = false
                    },
                    enabled = topicName.trim().isNotBlank()
                ) {
                    Text(stringResource(R.string.add_topic))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    topicBeingEdited?.let { topic ->
        TopicSettingsDialog(
            topic = topic,
            onDismiss = { topicBeingEdited = null },
            onSave = { updated ->
                viewModel.updateTopic(updated)
                topicBeingEdited = null
            }
        )
    }

    if (showLogView) {
        LogViewDialog(onDismiss = { showLogView = false })
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
}

@Composable
private fun TopicItem(
    topic: Topic,
    latestMessage: MessageEntity?,
    showSettings: Boolean,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = topic.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Row {
                    if (showSettings) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.topic_settings)
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                }
            }

            latestMessage?.let { message ->
                Text(
                    text = message.message ?: message.title ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (message.attachmentUrl != null) {
                    val attachmentText = when (message.attachmentDownloadState) {
                        com.runerback.ntfyclient.data.local.db.AttachmentDownloadState.DOWNLOADED ->
                            stringResource(R.string.attachment_downloaded)
                        else -> stringResource(R.string.attachment_pending)
                    }
                    Text(
                        text = attachmentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicSettingsDialog(
    topic: Topic,
    onDismiss: () -> Unit,
    onSave: (Topic) -> Unit
) {
    var enabled by remember { mutableStateOf(topic.enabled) }
    var notify by remember { mutableStateOf(topic.notify) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.topic_settings)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                    Text(
                        text = stringResource(R.string.enabled),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = notify,
                        onCheckedChange = { notify = it }
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(R.string.notify),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.notify_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(topic.copy(enabled = enabled, notify = notify)) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EmptyTopics(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_topics),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
