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
fun GrantAccessDialog(
    title: String,
    targetLabel: String,
    targetValue: String,
    readOnlyTarget: Boolean = false,
    confirmText: String = "Grant",
    dismissText: String = "Cancel",
    onConfirm: (target: String, permission: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf(targetValue) }
    var permission by remember { mutableStateOf("read-write") }
    var expanded by remember { mutableStateOf(false) }
    val permissions = listOf("read-only", "write-only", "read-write")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = target,
                    onValueChange = { if (!readOnlyTarget) target = it },
                    label = { Text(targetLabel) },
                    readOnly = readOnlyTarget,
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
                onClick = { onConfirm(target, permission) },
                enabled = target.isNotBlank(),
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
