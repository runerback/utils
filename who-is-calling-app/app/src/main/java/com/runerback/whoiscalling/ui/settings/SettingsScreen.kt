package com.runerback.whoiscalling.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.whoiscalling.data.settings.LookupSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()

    var urlTemplate by remember(settings.apiUrlTemplate) {
        mutableStateOf(settings.apiUrlTemplate)
    }
    val urlValid = urlTemplate.contains(LookupSettings.NUMBER_PLACEHOLDER)

    var testNumber by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = urlTemplate,
                onValueChange = { urlTemplate = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Lookup API URL") },
                placeholder = { Text("https://api.example.com/lookup?num={number}") },
                supportingText = {
                    if (urlValid) {
                        Text("{number} is replaced by the incoming caller's number. The API must return JSON like {\"isSpam\":false,\"sourceArea\":\"Shanghai\",\"sourceName\":\"Delivery\"}.")
                    } else {
                        Text(
                            "URL must contain the ${LookupSettings.NUMBER_PLACEHOLDER} placeholder",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                isError = !urlValid,
                singleLine = true,
            )
            Button(
                onClick = { viewModel.setApiUrlTemplate(urlTemplate) },
                enabled = urlValid && urlTemplate != settings.apiUrlTemplate,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (urlTemplate == settings.apiUrlTemplate) "Saved" else "Save URL")
            }

            Column {
                Text(
                    "Lookup timeout: %.1f s".format(settings.timeoutMillis / 1000f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = settings.timeoutMillis.toFloat(),
                    onValueChange = { viewModel.setTimeoutMillis(it.toLong()) },
                    valueRange = LookupSettings.MIN_TIMEOUT_MILLIS.toFloat()..
                        LookupSettings.MAX_TIMEOUT_MILLIS.toFloat(),
                    steps = 6,
                )
                Text(
                    "The system gives call screening apps about 5 seconds to respond, so the lookup is capped at 4 s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ToggleRow(
                title = "Block spam calls",
                subtitle = "Automatically reject calls when the API reports isSpam=true",
                checked = settings.blockSpam,
                onCheckedChange = viewModel::setBlockSpam,
            )

            ToggleRow(
                title = "Notify on lookup failure",
                subtitle = "Show a notification when the lookup times out or errors",
                checked = settings.notifyOnLookupFailure,
                onCheckedChange = viewModel::setNotifyOnLookupFailure,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Test lookup", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = testNumber,
                            onValueChange = {
                                testNumber = it
                                viewModel.resetTestState()
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Phone number") },
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Button(
                            onClick = { viewModel.testLookup(testNumber) },
                            enabled = testNumber.isNotBlank() &&
                                settings.apiUrlTemplate.contains(LookupSettings.NUMBER_PLACEHOLDER) &&
                                testState !is TestLookupState.Running,
                        ) {
                            Text("Test")
                        }
                    }
                    when (val state = testState) {
                        TestLookupState.Idle -> Unit
                        TestLookupState.Running -> Text("Looking up…")
                        is TestLookupState.Success -> Text(
                            "isSpam=${state.result.isSpam}, sourceName=${state.result.sourceName}, sourceArea=${state.result.sourceArea}",
                        )
                        is TestLookupState.Failure -> Text(
                            "Failed: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
