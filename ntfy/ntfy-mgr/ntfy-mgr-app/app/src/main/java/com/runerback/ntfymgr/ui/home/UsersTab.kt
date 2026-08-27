package com.runerback.ntfymgr.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.ntfymgr.data.remote.UserItem
import com.runerback.ntfymgr.ui.components.PromptDialog

@Composable
fun UsersTab(
    users: List<UserItem>,
    onCreateUser: (String, String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onGrantAccess: (String, String, String) -> Unit,
    onRevokeAccess: (String, String) -> Unit,
    onCreateToken: (String, String, String) -> Unit,
    onDeleteToken: (String, String) -> Unit,
) {
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var grantDialogUser by remember { mutableStateOf<UserItem?>(null) }
    var tokenDialogUser by remember { mutableStateOf<UserItem?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newUsername,
                onValueChange = { newUsername = it },
                label = { Text("Username") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Password") },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Button(
                onClick = {
                    onCreateUser(newUsername, newPassword)
                    newUsername = ""
                    newPassword = ""
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Add")
            }
        }

        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(users, key = { it.name }) { user ->
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${user.name} (${user.role})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { onDeleteUser(user.name) },
                            ) {
                                Text("Delete")
                            }
                        }

                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = { grantDialogUser = user }) {
                                Text("Grant")
                            }
                            Button(
                                onClick = { tokenDialogUser = user },
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text("Token")
                            }
                            Button(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(if (expanded) "Hide" else "Details")
                            }
                        }

                        if (expanded) {
                            Text(
                                text = "Access:",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            if (user.accesses.isEmpty()) {
                                Text("None")
                            } else {
                                user.accesses.forEach { access ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "${access.permission} on ${access.topic}",
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(
                                            onClick = { onRevokeAccess(user.name, access.topic) },
                                        ) {
                                            Text("Revoke")
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Tokens:",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            if (user.tokens.isEmpty()) {
                                Text("None")
                            } else {
                                user.tokens.forEach { token ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "${token.value} — ${token.expires}",
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(
                                            onClick = { onDeleteToken(user.name, token.value) },
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    grantDialogUser?.let { user ->
        PromptDialog(
            title = "Grant access for ${user.name}",
            fields = listOf(
                "Topic" to "",
                "Permission (read-write/read-only/write-only)" to "read-write",
            ),
            onConfirm = { values ->
                onGrantAccess(user.name, values[0], values[1])
                grantDialogUser = null
            },
            onDismiss = { grantDialogUser = null },
        )
    }

    tokenDialogUser?.let { user ->
        PromptDialog(
            title = "Add token for ${user.name}",
            fields = listOf(
                "Expires (e.g. 30d), optional" to "",
                "Label, optional" to "",
            ),
            onConfirm = { values ->
                onCreateToken(user.name, values[0], values[1])
                tokenDialogUser = null
            },
            onDismiss = { tokenDialogUser = null },
        )
    }
}
