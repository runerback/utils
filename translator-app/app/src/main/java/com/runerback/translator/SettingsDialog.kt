package com.runerback.translator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.translator.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    settingsRepository: SettingsRepository,
    onDismiss: () -> Unit,
) {
    val baseUrl by settingsRepository.baseUrl.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_BASE_URL)
    val useFakeServer by settingsRepository.useFakeServer.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    var urlInput by remember(baseUrl) { mutableStateOf(baseUrl) }
    var fakeInput by remember(useFakeServer) { mutableStateOf(useFakeServer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = Color.Black) },
        containerColor = Color.White,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Ollama base URL", color = Color.Black) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Use fake dev server",
                    color = Color.Black,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Switch(
                    checked = fakeInput,
                    onCheckedChange = { fakeInput = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Black,
                    ),
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        settingsRepository.setBaseUrl(urlInput)
                        settingsRepository.setUseFakeServer(fakeInput)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Cancel")
            }
        },
    )
}
