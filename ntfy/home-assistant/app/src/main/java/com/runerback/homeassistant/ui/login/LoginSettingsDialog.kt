package com.runerback.homeassistant.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runerback.homeassistant.R
import com.runerback.homeassistant.data.local.SettingsRepository
import com.runerback.homeassistant.data.remote.ApiClient
import com.runerback.homeassistant.util.NetworkErrors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun LoginSettingsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val settingsRepository = remember { SettingsRepository(context) }
    var serverUrl by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        serverUrl = settingsRepository.serverUrl.first()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        testResult = null
                    },
                    label = { Text(stringResource(R.string.server_url)) },
                    placeholder = { Text(stringResource(R.string.server_url_hint)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            scope.launch {
                                settingsRepository.setServerUrl(serverUrl.trim().trimEnd('/'))
                                onDismiss()
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                testResult?.let { result ->
                    Text(
                        text = result,
                        color = if (testSuccess) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            scope.launch {
                                testing = true
                                testResult = null
                                val url = serverUrl.trim().trimEnd('/')
                                ApiClient.testConnection(url)
                                    .onSuccess {
                                        testSuccess = true
                                        testResult = it
                                    }
                                    .onFailure {
                                        testSuccess = false
                                        testResult = NetworkErrors.describe(it, url)
                                    }
                                testing = false
                            }
                        },
                        enabled = !testing && serverUrl.isNotBlank(),
                    ) {
                        Text(
                            stringResource(
                                if (testing) R.string.testing else R.string.test_connection
                            )
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            scope.launch {
                                settingsRepository.setServerUrl(serverUrl.trim().trimEnd('/'))
                                onDismiss()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
