package com.runerback.translator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runerback.translator.dictionary.DictionaryImporter
import com.runerback.translator.dictionary.DictionaryInfo
import com.runerback.translator.dictionary.DictionaryStoreProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.subread.dictionary.core.DictionaryFormatException
import java.io.IOException

/** One composable per translation provider; `SettingsDialog` picks one with `when`. */

@Composable
fun OllamaProviderSettings(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    showSimplify: Boolean,
    onShowSimplifyChange: (Boolean) -> Unit,
    useFakeServer: Boolean,
    onUseFakeServerChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        UrlTextField(value = baseUrl, onValueChange = onBaseUrlChange, label = "Ollama base URL")
        ServerToggles(showSimplify, onShowSimplifyChange, useFakeServer, onUseFakeServerChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArgosTranslateProviderSettings(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    sourceLanguage: String,
    onSourceLanguageChange: (String) -> Unit,
    targetLanguage: String,
    onTargetLanguageChange: (String) -> Unit,
    showSimplify: Boolean,
    onShowSimplifyChange: (Boolean) -> Unit,
    useFakeServer: Boolean,
    onUseFakeServerChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        UrlTextField(value = baseUrl, onValueChange = onBaseUrlChange, label = "ArgosTranslate server URL")
        LanguageDropdown("Source language", sourceLanguage, onSourceLanguageChange)
        LanguageDropdown("Default target language", targetLanguage, onTargetLanguageChange)
        ServerToggles(showSimplify, onShowSimplifyChange, useFakeServer, onUseFakeServerChange)
    }
}

/**
 * The imported dictionaries with their enabled toggles, plus import and delete. Self-contained
 * on purpose: toggles, imports and deletes write straight to the dictionary database, so they
 * apply immediately and are not part of the dialog's Save/Cancel state.
 */
@Composable
fun DictionaryProviderSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dictionaries by remember { mutableStateOf<List<DictionaryInfo>?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var importRows by remember { mutableStateOf(0) }
    var importError by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            dictionaries = withContext(Dispatchers.IO) { DictionaryStoreProvider.get(context).list() }
        }
    }
    LaunchedEffect(Unit) { reload() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importJob = scope.launch {
                importRows = 0
                importError = null
                try {
                    DictionaryImporter.importDictionary(context, DictionaryStoreProvider.get(context), uri) { rows ->
                        importRows = rows
                    }
                    reload()
                } catch (e: CancellationException) {
                    importError = "Import cancelled"
                } catch (e: DictionaryFormatException) {
                    importError = e.message
                } catch (e: IOException) {
                    importError = "Could not read the file: ${e.message}"
                } finally {
                    importJob = null
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Button(
            onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream")) },
            enabled = importJob == null,
        ) {
            Text("Import dictionary (.zip)")
        }
        if (importJob != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Importing... $importRows rows",
                    color = Color.Black,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { importJob?.cancel() }) {
                    Text("Cancel", color = Color.Black)
                }
            }
        }
        importError?.let { error ->
            Text(error, color = Color.Red, fontSize = 12.sp)
        }
        val list = dictionaries
        when {
            list == null -> Text("Loading dictionaries...", color = Color.Black)
            list.isEmpty() -> Text("No dictionaries — import a Yomitan .zip to begin", color = Color.Black)
            else -> list.forEach { dictionary ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = dictionary.enabled,
                        onCheckedChange = { checked ->
                            scope.launch {
                                withContext(Dispatchers.IO) { DictionaryStoreProvider.get(context).setEnabled(dictionary.id, checked) }
                                reload()
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Black,
                            uncheckedColor = Color.Black,
                            checkmarkColor = Color.White,
                        ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dictionary.title, color = Color.Black)
                        Text(
                            "${dictionary.revision} · ${dictionary.terms} entries",
                            color = Color.DarkGray,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { DictionaryStoreProvider.get(context).deleteDictionary(dictionary.id) }
                            reload()
                        }
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${dictionary.title}",
                            tint = Color.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Black) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = settingsTextFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(label: String, value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextField(
            value = value.uppercase(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = Color.Black) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = settingsTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .menuAnchor(MenuAnchorType.PrimaryEditable, true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            listOf("en", "zh").forEach { code ->
                DropdownMenuItem(
                    text = { Text(code.uppercase()) },
                    onClick = {
                        onValueChange(code)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ServerToggles(
    showSimplify: Boolean,
    onShowSimplifyChange: (Boolean) -> Unit,
    useFakeServer: Boolean,
    onUseFakeServerChange: (Boolean) -> Unit,
) {
    SettingsCheckRow("Show Simplify button", showSimplify, onShowSimplifyChange)
    SettingsCheckRow("Use fake dev server", useFakeServer, onUseFakeServerChange)
}

@Composable
private fun SettingsCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color.Black,
                uncheckedColor = Color.Black,
                checkmarkColor = Color.White,
            ),
        )
        Text(text = label, color = Color.Black)
    }
}

@Composable
private fun settingsTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
)
