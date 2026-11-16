package com.runerback.homeassistant.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.runerback.homeassistant.R
import com.runerback.homeassistant.util.renderQrBitmap

@Composable
fun PairingDialog(
    deviceName: String,
    status: DevicesViewModel.PairingStatus,
    qrPayload: String?,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
) {
    var name by remember(deviceName) { mutableStateOf(deviceName) }

    val title = when (status) {
        DevicesViewModel.PairingStatus.SUCCESS -> stringResource(R.string.pairing_success_title)
        DevicesViewModel.PairingStatus.ERROR -> stringResource(R.string.pairing_error_title)
        DevicesViewModel.PairingStatus.PAIRING -> stringResource(R.string.pairing_progress_title)
        else -> stringResource(R.string.pair_device)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (status) {
                    DevicesViewModel.PairingStatus.PAIRING -> {
                        // The ESP32-CAM may take a while to scan; don't let the
                        // screen turn off while the QR is up.
                        val view = LocalView.current
                        DisposableEffect(Unit) {
                            view.keepScreenOn = true
                            onDispose { view.keepScreenOn = false }
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                        if (qrPayload != null) {
                            val sizePx = with(LocalDensity.current) { 240.dp.roundToPx() }
                            val qrBitmap = remember(qrPayload) { renderQrBitmap(qrPayload, sizePx) }
                            Surface(
                                color = Color.White,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.pairing_qr_description),
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(240.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = stringResource(
                                if (qrPayload != null) R.string.pairing_scan_qr_hint
                                else R.string.pairing_preparing_qr
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    DevicesViewModel.PairingStatus.SUCCESS -> {
                        Text(
                            text = stringResource(R.string.pairing_success_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DevicesViewModel.PairingStatus.ERROR -> {
                        Text(
                            text = error ?: stringResource(R.string.pairing_error_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.device_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (status) {
                DevicesViewModel.PairingStatus.SUCCESS -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
                DevicesViewModel.PairingStatus.ERROR -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
                DevicesViewModel.PairingStatus.PAIRING -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                else -> {
                    TextButton(onClick = { onConfirm(name) }) {
                        Text(stringResource(R.string.start_pairing))
                    }
                }
            }
        },
        dismissButton = {
            if (status != DevicesViewModel.PairingStatus.PAIRING && status != DevicesViewModel.PairingStatus.SUCCESS) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}
