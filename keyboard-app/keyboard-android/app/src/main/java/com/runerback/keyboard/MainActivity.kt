package com.runerback.keyboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.runerback.keyboard.data.SettingsRepository
import com.runerback.keyboard.network.KeyboardClient
import com.runerback.keyboard.ui.screens.KeyboardScreen
import com.runerback.keyboard.ui.theme.KeyboardTheme
import com.runerback.keyboard.util.LogManager

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.d(TAG, "onCreate")
        enableEdgeToEdge()
        hideNavigationBars()

        val host = SettingsRepository.readHost()
        val port = SettingsRepository.readPort()
        if (host.isNotBlank()) {
            KeyboardClient.sendConfig(SettingsRepository.readInterceptRealKeyboard())
            KeyboardClient.connect(host, port, SettingsRepository.readDeviceToken())
        }

        setContent {
            val connectionState by KeyboardClient.state.collectAsState()
            val authState by KeyboardClient.authState.collectAsState()
            KeyboardTheme {
                KeyboardScreen(
                    onKeyEvent = { vk, action ->
                        KeyboardClient.sendKey(vk, action)
                    },
                    connectionState = connectionState,
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )

                if (authState is KeyboardClient.AuthState.PairingRequired) {
                    PairingDialog(
                        onPair = { KeyboardClient.sendPair(it) }
                    )
                }
            }
        }
    }

    @Composable
    private fun PairingDialog(
        onPair: (String) -> Unit
    ) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.pairing_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.pairing_message))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text(stringResource(R.string.pairing_code_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onPair(input) },
                    enabled = input.length == 6
                ) {
                    Text(stringResource(R.string.pair))
                }
            },
            dismissButton = {
                TextButton(onClick = { KeyboardClient.disconnect() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }

    override fun onResume() {
        super.onResume()
        LogManager.d(TAG, "onResume")
        val host = SettingsRepository.readHost()
        val port = SettingsRepository.readPort()
        if (host.isNotBlank() && KeyboardClient.state.value !is KeyboardClient.State.Connected) {
            KeyboardClient.sendConfig(SettingsRepository.readInterceptRealKeyboard())
            KeyboardClient.connect(host, port, SettingsRepository.readDeviceToken())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBars()
    }

    private fun hideNavigationBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
