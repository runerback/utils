package com.runerback.ntfymgr.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is UiState.Ready && (uiState as UiState.Ready).message != null) {
            snackbarHostState.showSnackbar((uiState as UiState.Ready).message!!)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ntfy Manager") },
                actions = {
                    if (uiState !is UiState.Login) {
                        Button(onClick = { viewModel.logout() }) {
                            Text("Logout")
                        }
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
                    serverUrl = serverUrl,
                    error = null,
                    onLogin = { username, password ->
                        viewModel.saveServerUrl(serverUrl)
                        viewModel.login(username, password)
                    },
                    onServerUrlChange = { viewModel.setServerUrl(it) },
                )

                is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(padding))

                is UiState.Error -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding),
                )

                is UiState.Ready -> ManagerContent(
                    users = state.users,
                    topics = state.topics,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun ManagerContent(
    users: List<com.runerback.ntfymgr.data.remote.UserItem>,
    topics: List<com.runerback.ntfymgr.data.remote.TopicItem>,
    viewModel: HomeViewModel,
) {
    val selectedTab = remember { mutableIntStateOf(0) }
    val tabs = listOf("Users", "Topics")

    TabRow(selectedTabIndex = selectedTab.intValue) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab.intValue == index,
                onClick = { selectedTab.intValue = index },
                text = { Text(title) },
            )
        }
    }

    when (selectedTab.intValue) {
        0 -> UsersTab(
            users = users,
            onCreateUser = { username, password -> viewModel.createUser(username, password) { viewModel.load() } },
            onDeleteUser = { viewModel.deleteUser(it) },
            onGrantAccess = { name, topic, permission -> viewModel.grantUserAccess(name, topic, permission) },
            onRevokeAccess = { name, topic -> viewModel.revokeUserAccess(name, topic) },
            onCreateToken = { name, expires, label -> viewModel.createUserToken(name, expires, label) },
            onDeleteToken = { name, token -> viewModel.deleteUserToken(name, token) },
        )

        1 -> TopicsTab(
            topics = topics,
            users = users,
            onCreateTopic = { topic, username, permission -> viewModel.createTopic(topic, username, permission) },
            onDeleteTopic = { viewModel.deleteTopic(it) },
            onGrantAccess = { topic, username, permission -> viewModel.grantTopicAccess(topic, username, permission) },
            onRevokeAccess = { topic, username -> viewModel.revokeTopicAccess(topic, username) },
        )
    }
}
