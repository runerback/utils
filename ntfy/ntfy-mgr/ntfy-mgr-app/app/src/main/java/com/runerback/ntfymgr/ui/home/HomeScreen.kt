package com.runerback.ntfymgr.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.runerback.ntfymgr.data.remote.TopicItem
import com.runerback.ntfymgr.data.remote.UserItem
import com.runerback.ntfymgr.ui.components.AddUserDialog
import com.runerback.ntfymgr.ui.components.CreateTopicDialog
import com.runerback.ntfymgr.ui.components.LoadingIndicator
import com.runerback.ntfymgr.ui.components.LogViewDialog
import com.runerback.ntfymgr.ui.settings.SettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLogView by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val message = (uiState as? UiState.Ready)?.message
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ntfy Manager") },
                actions = {
                    IconButton(onClick = { showLogView = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Logs",
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                is UiState.Login -> LoginScreen(
                    error = null,
                    onLogin = { username, password -> viewModel.login(username, password) },
                )

                is UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }

                is UiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = { viewModel.load() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("Retry")
                    }
                    if (state.message.contains("Server URL", ignoreCase = true)) {
                        Button(
                            onClick = { showSettings = true },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text("Open Settings")
                        }
                    }
                }

                is UiState.Ready -> ManagerContent(
                    users = state.users,
                    topics = state.topics,
                    viewModel = viewModel,
                )
            }
        }
    }

    if (showLogView) {
        LogViewDialog(onDismiss = { showLogView = false })
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = {
                showSettings = false
                viewModel.refreshServerUrl()
            },
            onLogout = {
                showSettings = false
                viewModel.logout()
            },
            onServerUrlChanged = {
                showSettings = false
                viewModel.onServerUrlChanged()
            },
        )
    }
}

@Composable
private fun ManagerContent(
    users: List<UserItem>,
    topics: List<TopicItem>,
    viewModel: HomeViewModel,
) {
    val selectedTab = remember { mutableIntStateOf(0) }
    val tabs = listOf("Users", "Topics")
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showCreateTopicDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    TabRow(selectedTabIndex = selectedTab.intValue) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab.intValue == index
            Tab(
                selected = isSelected,
                onClick = { selectedTab.intValue = index },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(title)
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = if (index == 0) "Add user" else "Create topic",
                                modifier = Modifier.clickable {
                                    if (index == 0) {
                                        showAddUserDialog = true
                                    } else {
                                        showCreateTopicDialog = true
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    when (selectedTab.intValue) {
        0 -> UsersTab(
            users = users,
            topics = topics,
            onDeleteUser = { viewModel.deleteUser(it) },
            onGrantAccess = { name, topic, permission -> viewModel.grantUserAccess(name, topic, permission) },
            onRevokeAccess = { name, topic -> viewModel.revokeUserAccess(name, topic) },
            onCreateToken = { name, expires, label -> viewModel.createUserToken(name, expires, label) },
            onDeleteToken = { name, token -> viewModel.deleteUserToken(name, token) },
            onCopyToken = { token ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("token", token)))
                    viewModel.showMessage("Copied")
                }
            },
        )

        1 -> TopicsTab(
            topics = topics,
            users = users,
            onDeleteTopic = { viewModel.deleteTopic(it) },
            onGrantAccess = { topic, username, permission -> viewModel.grantTopicAccess(topic, username, permission) },
            onRevokeAccess = { topic, username -> viewModel.revokeTopicAccess(topic, username) },
        )
    }

    if (showCreateTopicDialog) {
        CreateTopicDialog(
            title = "Create topic",
            existingTopics = topics.map { it.name },
            onConfirm = { topic, username, permission ->
                viewModel.createTopic(topic, username, permission)
                showCreateTopicDialog = false
            },
            onDismiss = { showCreateTopicDialog = false },
        )
    }

    if (showAddUserDialog) {
        AddUserDialog(
            title = "Add user",
            onConfirm = { username, password ->
                viewModel.createUser(username, password)
                showAddUserDialog = false
            },
            onDismiss = { showAddUserDialog = false },
        )
    }
}
