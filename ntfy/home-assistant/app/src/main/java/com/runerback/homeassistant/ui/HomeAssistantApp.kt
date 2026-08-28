package com.runerback.homeassistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.homeassistant.R
import com.runerback.homeassistant.data.AuthManager
import com.runerback.homeassistant.data.local.SettingsRepository
import com.runerback.homeassistant.ui.components.LogViewDialog
import com.runerback.homeassistant.ui.devices.DevicesScreen
import com.runerback.homeassistant.ui.home.HomeScreen
import com.runerback.homeassistant.ui.login.LoginScreen
import com.runerback.homeassistant.ui.login.LoginViewModel
import com.runerback.homeassistant.ui.messages.MessagesScreen
import com.runerback.homeassistant.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAssistantApp() {
    val username by AuthManager.username.collectAsState()

    if (username == null) {
        val loginViewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
        LoginScreen(viewModel = loginViewModel)
        return
    }

    Dashboard()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showLogView by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Text(text = AuthManager.username.value ?: "")
                    IconButton(onClick = { showLogView = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.logs)
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val settingsRepository = SettingsRepository(context)
                                val baseUrl = settingsRepository.serverUrl.first()
                                AuthManager.logout(baseUrl)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.logout)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.home)) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                    label = { Text(stringResource(R.string.messages)) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DevicesOther, contentDescription = null) },
                    label = { Text(stringResource(R.string.devices)) },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HomeScreen(modifier = Modifier.padding(innerPadding))
            1 -> MessagesScreen(modifier = Modifier.padding(innerPadding))
            2 -> DevicesScreen(modifier = Modifier.padding(innerPadding))
            3 -> SettingsScreen(modifier = Modifier.padding(innerPadding))
        }
    }

    if (showLogView) {
        LogViewDialog(onDismiss = { showLogView = false })
    }
}
