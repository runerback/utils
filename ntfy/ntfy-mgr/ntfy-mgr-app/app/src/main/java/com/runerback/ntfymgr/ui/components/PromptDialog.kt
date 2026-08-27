package com.runerback.ntfymgr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun PromptDialog(
    title: String,
    fields: List<Pair<String, String>>,
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var values by remember { mutableStateOf(fields.map { it.second }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                fields.forEachIndexed { index, pair ->
                    OutlinedTextField(
                        value = values.getOrElse(index) { "" },
                        onValueChange = { newValue ->
                            values = values.toMutableList().apply { set(index, newValue) }
                        },
                        label = { Text(pair.first) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(values) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}
