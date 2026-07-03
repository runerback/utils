package com.runerback.brownnoise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.runerback.brownnoise.ui.StreamScreen
import com.runerback.brownnoise.ui.logs.LogsScreen
import com.runerback.brownnoise.ui.settings.SettingsScreen
import com.runerback.brownnoise.ui.theme.BrownNoiseTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            BrownNoiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen = remember { mutableStateOf(Screen.Stream) }
                    BackHandler(enabled = currentScreen.value != Screen.Stream) {
                        currentScreen.value = Screen.Stream
                    }
                    when (currentScreen.value) {
                        Screen.Stream -> StreamScreen(
                            onNavigateToSettings = { currentScreen.value = Screen.Settings },
                            onNavigateToLogs = { currentScreen.value = Screen.Logs }
                        )
                        Screen.Settings -> SettingsScreen(
                            onNavigateBack = { currentScreen.value = Screen.Stream }
                        )
                        Screen.Logs -> LogsScreen(
                            onNavigateBack = { currentScreen.value = Screen.Stream }
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    private enum class Screen {
        Stream,
        Settings,
        Logs
    }
}
