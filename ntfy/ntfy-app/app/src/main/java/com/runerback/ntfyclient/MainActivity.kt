package com.runerback.ntfyclient

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.runerback.ntfyclient.data.local.SettingsRepository
import com.runerback.ntfyclient.service.SubscriptionForegroundService
import com.runerback.ntfyclient.ui.NtfyClientScreen
import com.runerback.ntfyclient.ui.theme.NtfyClientTheme
import com.runerback.ntfyclient.work.SubscriptionServiceWatchdogWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled gracefully; notifications will only post if granted.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        startBackgroundServiceIfEnabled()
        enableEdgeToEdge()
        setContent {
            NtfyClientTheme {
                NtfyClientScreen()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startBackgroundServiceIfEnabled() {
        lifecycleScope.launch {
            val settingsRepository = SettingsRepository(this@MainActivity)
            if (settingsRepository.backgroundListeningEnabled.first()) {
                SubscriptionForegroundService.start(this@MainActivity)
                SubscriptionServiceWatchdogWorker.schedule(this@MainActivity)
            }
        }
    }
}
