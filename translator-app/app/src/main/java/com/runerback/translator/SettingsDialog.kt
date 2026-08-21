package com.runerback.translator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
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
import com.runerback.translator.data.SettingsManager
import com.runerback.translator.data.SettingsRepository
import com.runerback.translator.translate.TranslationProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settingsRepository: SettingsRepository,
    onDismiss: () -> Unit,
) {
    val baseUrl by settingsRepository.baseUrl.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_BASE_URL)
    val translationProvider by settingsRepository.translationProvider.collectAsStateWithLifecycle(initialValue = TranslationProvider.OLLAMA)
    val argosTranslateBaseUrl by settingsRepository.argosTranslateBaseUrl.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_ARGOSTRANSLATE_BASE_URL)
    val showSimplifyButton by settingsRepository.showSimplifyButton.collectAsStateWithLifecycle(initialValue = true)
    val useFakeServer by settingsRepository.useFakeServer.collectAsStateWithLifecycle(initialValue = false)
    val sourceLanguage by settingsRepository.sourceLanguage.collectAsStateWithLifecycle(initialValue = SettingsRepository.DEFAULT_SOURCE_LANGUAGE)
    val readerDebugMode by SettingsManager.readerDebugMode.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    var urlInput by remember(baseUrl) { mutableStateOf(baseUrl) }
    var providerInput by remember(translationProvider) { mutableStateOf(translationProvider) }
    var argosUrlInput by remember(argosTranslateBaseUrl) { mutableStateOf(argosTranslateBaseUrl) }
    var simplifyInput by remember(showSimplifyButton) { mutableStateOf(showSimplifyButton) }
    var fakeInput by remember(useFakeServer) { mutableStateOf(useFakeServer) }
    var sourceLanguageInput by remember(sourceLanguage) { mutableStateOf(sourceLanguage) }
    var debugInput by remember(readerDebugMode) { mutableStateOf(readerDebugMode) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var sourceLanguageDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = Color.Black) },
        containerColor = Color.White,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = providerDropdownExpanded,
                    onExpandedChange = { providerDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextField(
                        value = providerInput.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Translation provider", color = Color.Black) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                    )
                    ExposedDropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false },
                    ) {
                        TranslationProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    providerInput = provider
                                    providerDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                if (providerInput == TranslationProvider.OLLAMA) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                } else {
                    TextField(
                        value = argosUrlInput,
                        onValueChange = { argosUrlInput = it },
                        label = { Text("ArgosTranslate server URL", color = Color.Black) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }

                if (providerInput == TranslationProvider.ARGOSTRANSLATE) {
                    ExposedDropdownMenuBox(
                        expanded = sourceLanguageDropdownExpanded,
                        onExpandedChange = { sourceLanguageDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextField(
                            value = sourceLanguageInput.uppercase(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Source language", color = Color.Black) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceLanguageDropdownExpanded) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                        )
                        ExposedDropdownMenu(
                            expanded = sourceLanguageDropdownExpanded,
                            onDismissRequest = { sourceLanguageDropdownExpanded = false },
                        ) {
                            listOf("en", "zh").forEach { code ->
                                DropdownMenuItem(
                                    text = { Text(code.uppercase()) },
                                    onClick = {
                                        sourceLanguageInput = code
                                        sourceLanguageDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Show Simplify button",
                    color = Color.Black,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Switch(
                    checked = simplifyInput,
                    onCheckedChange = { simplifyInput = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Black,
                    ),
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
                Text(
                    text = "Reader debug mode",
                    color = Color.Black,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Switch(
                    checked = debugInput,
                    onCheckedChange = { debugInput = it },
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
                        settingsRepository.setTranslationProvider(providerInput)
                        settingsRepository.setArgosTranslateBaseUrl(argosUrlInput)
                        settingsRepository.setSourceLanguage(sourceLanguageInput)
                        settingsRepository.setShowSimplifyButton(simplifyInput)
                        settingsRepository.setUseFakeServer(fakeInput)
                        SettingsManager.setReaderDebugMode(debugInput)
                        onDismiss()
                    }
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
