package com.runerback.homeassistant.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.homeassistant.R
import com.runerback.homeassistant.data.remote.model.BleDevice
import com.runerback.homeassistant.data.remote.model.Device

@Composable
fun DevicesScreen(
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = viewModel(factory = DevicesViewModel.Factory),
) {
    val devices by viewModel.devices.collectAsState()
    val scanDevices by viewModel.scanDevices.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val pairingDevice by viewModel.pairingDevice.collectAsState()
    val pairingStatus by viewModel.pairingStatus.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.devices),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Button(
                    onClick = { viewModel.startScan() },
                    enabled = !scanning,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(if (scanning) R.string.scanning else R.string.add_device))
                }
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (scanning || scanDevices.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.bluetooth_devices),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (scanDevices.isEmpty() && scanning) {
                    Text(
                        text = stringResource(R.string.looking_for_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(scanDevices, key = { it.address }) { device ->
                        BleDeviceCard(
                            device = device,
                            onPair = { viewModel.selectDevice(device) },
                        )
                    }
                }
                if (scanning) {
                    Button(
                        onClick = { viewModel.stopScan() },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.stop))
                    }
                }
            }

            Text(
                text = stringResource(R.string.registered_devices),
                style = MaterialTheme.typography.titleMedium,
            )

            if (loading && devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(devices, key = { it.deviceId }) { device ->
                        DeviceCard(device = device)
                    }
                }
            }
        }

        pairingDevice?.let { device ->
            PairingDialog(
                device = device,
                status = pairingStatus,
                error = error,
                onDismiss = { viewModel.dismissPairing() },
                onConfirm = { name, ssid, password ->
                    viewModel.pairDevice(name, ssid, password)
                },
            )
        }
    }
}

@Composable
private fun BleDeviceCard(
    device: BleDevice,
    onPair: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${device.address} · RSSI ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onPair) {
                Text(stringResource(R.string.pair))
            }
        }
    }
}

@Composable
private fun DeviceCard(device: Device) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.deviceId,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(status = device.status)
            }
            Text(
                text = "${device.bleMac ?: stringResource(R.string.no_ble_address)} · ${stringResource(R.string.added)} ${formatTimestamp(device.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (label, color) = when (status) {
        "active" -> stringResource(R.string.status_active) to MaterialTheme.colorScheme.primary
        "pending_claim" -> stringResource(R.string.status_pending_claim) to MaterialTheme.colorScheme.tertiary
        "pending_status" -> stringResource(R.string.status_pending_status) to MaterialTheme.colorScheme.tertiary
        "failed" -> stringResource(R.string.status_failed) to MaterialTheme.colorScheme.error
        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

private fun formatTimestamp(ts: Double): String {
    val instant = java.time.Instant.ofEpochSecond(ts.toLong())
    return java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        .format(java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()))
}
