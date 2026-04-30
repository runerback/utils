package com.runerback.screenrecorder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.screenrecorder.ui.RecorderScreen
import com.runerback.screenrecorder.ui.RecorderViewModel
import com.runerback.screenrecorder.ui.theme.ScreenRecorderTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<RecorderViewModel>()
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var captureLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var overlaySettingsLauncher: ActivityResultLauncher<Intent>
    private var autoStartAfterCapturePermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        registerLaunchers()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val recordings by viewModel.recordings.collectAsStateWithLifecycle()
            val overlayGranted by viewModel.overlayGranted.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.lastOutputUri) {
                viewModel.refreshRecordings()
            }

            ScreenRecorderTheme {
                RecorderScreen(
                    uiState = uiState,
                    settings = settings,
                    recordings = recordings,
                    overlayEnabled = overlayGranted,
                    onEnterRecording = { beginEnterRecording(autoStart = false) },
                    onResolutionPresetSelected = viewModel::updateResolutionPreset,
                    onFrameRatePresetSelected = viewModel::updateFrameRatePreset,
                    onCaptureSystemAudioChanged = viewModel::updateCaptureSystemAudio,
                    onRefreshRecordings = {
                        viewModel.refreshRecordings()
                    },
                    onRequestOverlayPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        )
                        overlaySettingsLauncher.launch(intent)
                    },
                    onOpenRecording = { uri ->
                        openRecording(uri)
                    },
                    onShareRecording = { uri ->
                        shareRecording(uri)
                    },
                    onDeleteRecording = { uri ->
                        viewModel.deleteRecording(uri)
                    },
                    onDismissError = {
                        viewModel.dismissError()
                    },
                )
            }
        }

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun openRecording(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri) ?: "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this recording.", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareRecording(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri) ?: "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(intent, "Share recording"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can share this recording.", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerLaunchers() {
        captureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val launchedFromToolbox = autoStartAfterCapturePermission
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                if (launchedFromToolbox) {
                    viewModel.startRecording(this, result.resultCode, data)
                } else {
                    viewModel.showRecordingToolbox(this, result.resultCode, data)
                }
            } else {
                viewModel.reportError("Screen capture permission was cancelled.")
            }
            autoStartAfterCapturePermission = false
            if (launchedFromToolbox) {
                moveTaskToBack(true)
            }
        }

        permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            val deniedPermissions = result.filterValues { granted -> !granted }.keys
            if (deniedPermissions.isEmpty()) {
                captureLauncher.launch(projectionManager.createScreenCaptureIntent())
            } else {
                val launchedFromToolbox = autoStartAfterCapturePermission
                viewModel.reportError(
                    message = "Recording needs these permissions: ${deniedPermissions.joinToString()}",
                )
                autoStartAfterCapturePermission = false
                if (launchedFromToolbox) {
                    moveTaskToBack(true)
                }
            }
        }

        overlaySettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            viewModel.refreshOverlayPermission(this)
        }
    }

    private fun beginEnterRecording(autoStart: Boolean) {
        viewModel.refreshOverlayPermission(this)
        if (!viewModel.overlayGranted.value) {
            viewModel.reportError("Enable floating toolbox permission before entering recording.")
            return
        }

        autoStartAfterCapturePermission = autoStart
        val missingPermissions = buildList {
            if (viewModel.settings.value.captureSystemAudio &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missingPermissions.isEmpty()) {
            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            permissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_REQUEST_CAPTURE -> {
                beginEnterRecording(
                    autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_AFTER_PERMISSION, true),
                )
            }
        }
    }

    companion object {
        const val ACTION_REQUEST_CAPTURE = "com.runerback.screenrecorder.action.REQUEST_CAPTURE"
        const val EXTRA_AUTO_START_AFTER_PERMISSION = "extra_auto_start_after_permission"
    }
}
