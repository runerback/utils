package com.runerback.ntfymgr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTopicDialog(
    title: String,
    existingTopics: List<String> = emptyList(),
    confirmText: String = "Create",
    dismissText: String = "Cancel",
    onConfirm: (topic: String, username: String, permission: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var topic by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("read-write") }
    var expanded by remember { mutableStateOf(false) }
    var topicError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf(false) }
    val permissions = listOf("read-only", "write-only", "read-write")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = topic,
                    onValueChange = {
                        topic = it
                        topicError = null
                    },
                    label = { Text("Topic") },
                    isError = topicError != null,
                    supportingText = {
                        topicError?.let { Text(it) }
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        usernameError = false
                    },
                    label = { Text("Initial user") },
                    isError = usernameError,
                    supportingText = {
                        if (usernameError) {
                            Text("Username is required")
                        }
                    },
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = permission,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Permission") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = androidx.compose.ui.Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        permissions.forEach { option ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    permission = option
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    topicError = when {
                        topic.isBlank() -> "Topic is required"
                        topic in existingTopics -> "Topic already exists"
                        else -> null
                    }
                    usernameError = username.isBlank()
                    if (topicError == null && !usernameError) {
                        onConfirm(topic, username, permission)
                    }
                },
            ) {
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
