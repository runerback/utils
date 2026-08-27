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
fun AddUserDialog(
    title: String,
    confirmText: String = "Add",
    dismissText: String = "Cancel",
    onConfirm: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        usernameError = false
                    },
                    label = { Text("Username") },
                    isError = usernameError,
                    supportingText = {
                        if (usernameError) {
                            Text("Username is required")
                        }
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = false
                    },
                    label = { Text("Password") },
                    isError = passwordError,
                    supportingText = {
                        if (passwordError) {
                            Text("Password is required")
                        }
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    usernameError = username.isBlank()
                    passwordError = password.isBlank()
                    if (!usernameError && !passwordError) {
                        onConfirm(username, password)
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
