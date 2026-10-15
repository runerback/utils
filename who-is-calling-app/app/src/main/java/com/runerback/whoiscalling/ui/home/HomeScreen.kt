package com.runerback.whoiscalling.ui.home

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.whoiscalling.service.RecentLookup
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recentLookups by viewModel.recentLookups.collectAsStateWithLifecycle()

    var roleHeld by remember { mutableStateOf(checkScreeningRole(context)) }
    var notificationsGranted by remember { mutableStateOf(checkNotificationPermission(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        roleHeld = checkScreeningRole(context)
        notificationsGranted = checkNotificationPermission(context)
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        roleHeld = checkScreeningRole(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Who Is Calling") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!roleHeld) {
                item(key = "role_warning") {
                    WarningCard(
                        text = "Call screening role not granted. The app cannot see incoming calls until you grant it.",
                        buttonText = "Grant role",
                        onClick = {
                            val roleManager = context.getSystemService(RoleManager::class.java)
                            roleLauncher.launch(
                                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                            )
                        },
                    )
                }
            }
            if (!notificationsGranted) {
                item(key = "notification_warning") {
                    WarningCard(
                        text = "Notification permission not granted. Lookup results cannot be shown.",
                        buttonText = "Open app settings",
                        onClick = {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        },
                    )
                }
            }

            item(key = "enable") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Identify incoming calls", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (settings.apiUrlTemplate.isBlank()) "Set the API URL in settings first"
                                else "Look up the caller and show the result",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = viewModel::setEnabled,
                        )
                    }
                }
            }

            item(key = "recent_header") {
                Text(
                    "Recent lookups",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (recentLookups.isEmpty()) {
                item(key = "recent_empty") {
                    Text(
                        "No calls screened yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(recentLookups, key = { "${it.number}-${it.timestamp}" }) { lookup ->
                    RecentLookupRow(lookup)
                }
            }

            item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WarningCard(text: String, buttonText: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun RecentLookupRow(lookup: RecentLookup) {
    val isSpam = lookup.result?.isSpam == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSpam) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        buildString {
                            if (isSpam) append("⚠ SPAM · ")
                            append(lookup.result?.sourceName?.takeIf { it.isNotBlank() } ?: lookup.number)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val subtitle = buildList {
                        if (lookup.result?.sourceName != null) add(lookup.number)
                        lookup.result?.sourceArea?.takeIf { it.isNotBlank() }?.let { add(it) }
                        if (lookup.blocked) add("blocked")
                        lookup.error?.let { add("failed: $it") }
                    }.joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lookup.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun checkScreeningRole(context: android.content.Context): Boolean =
    context.getSystemService(RoleManager::class.java)
        ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

private fun checkNotificationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
