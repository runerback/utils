package com.runerback.homeassistant.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.runerback.homeassistant.R
import com.runerback.homeassistant.data.remote.model.BleDevice

@Composable
fun PairingDialog(
    device: BleDevice,
    status: DevicesViewModel.PairingStatus,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, ssid: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf(device.name) }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.pairing_scan_qr_hint),
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
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text(stringResource(R.string.wifi_ssid)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.wifi_password)) },
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
                    TextButton(
                        onClick = { onConfirm(name, ssid, password) },
                        enabled = ssid.isNotBlank() && password.isNotBlank(),
                    ) {
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
