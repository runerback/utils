package com.runerback.ntfymgr.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.ntfymgr.data.remote.TopicItem
import com.runerback.ntfymgr.data.remote.UserItem
import com.runerback.ntfymgr.ui.components.ConfirmDialog
import com.runerback.ntfymgr.ui.components.GrantAccessDialog
import com.runerback.ntfymgr.ui.components.PromptDialog

private data class GrantDialogState(
    val user: UserItem,
    val topic: String = "",
    val readOnlyTopic: Boolean = false,
)

@Composable
fun UsersTab(
    users: List<UserItem>,
    topics: List<TopicItem>,
    onDeleteUser: (String) -> Unit,
    onGrantAccess: (String, String, String) -> Unit,
    onRevokeAccess: (String, String) -> Unit,
    onCreateToken: (String, String, String) -> Unit,
    onDeleteToken: (String, String) -> Unit,
    onCopyToken: (String) -> Unit,
) {
    var grantDialog by remember { mutableStateOf<GrantDialogState?>(null) }
    var tokenDialogUser by remember { mutableStateOf<UserItem?>(null) }
    var deleteUserConfirm by remember { mutableStateOf<String?>(null) }
    var revokeAccessConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteTokenConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        LazyColumn {
            items(
                users.filter { it.role == "user" },
                key = { it.name }
            ) { user ->
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                            )
                            Column(modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)) {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "${user.accesses.size} accesses, ${user.tokens.size} tokens",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { deleteUserConfirm = user.name },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete user",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        if (expanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Accesses:",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { grantDialog = GrantDialogState(user) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Grant access",
                                    )
                                }
                            }

                            val accesses = user.accesses.sortedBy { it.topic }
                            if (accesses.isEmpty()) {
                                Text("No topics")
                            } else {
                                accesses.forEach { access ->
                                    val granted = access.permission != "no"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = access.topic,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (granted) {
                                            Text(
                                                text = access.permission,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Checkbox(
                                            checked = granted,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    grantDialog = GrantDialogState(
                                                        user = user,
                                                        topic = access.topic,
                                                        readOnlyTopic = true,
                                                    )
                                                } else {
                                                    revokeAccessConfirm = user.name to access.topic
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Tokens:",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (user.tokens.isEmpty()) {
                                    IconButton(
                                        onClick = { tokenDialogUser = user },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add token",
                                        )
                                    }
                                }
                            }
                            if (user.tokens.isEmpty()) {
                                Text("None")
                            } else {
                                user.tokens.forEach { token ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconButton(
                                            onClick = { onCopyToken(token.value) },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy token",
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(text = token.value)
                                            Text(
                                                text = token.expires,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = { deleteTokenConfirm = user.name to token.value },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete token",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
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

    grantDialog?.let { state ->
        GrantAccessDialog(
            title = "Grant access for ${state.user.name}",
            targetLabel = "Topic",
            targetValue = state.topic,
            readOnlyTarget = state.readOnlyTopic,
            onConfirm = { topic, permission ->
                onGrantAccess(state.user.name, topic, permission)
                grantDialog = null
            },
            onDismiss = { grantDialog = null },
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

    deleteUserConfirm?.let { username ->
        ConfirmDialog(
            title = "Delete user",
            text = "Are you sure you want to delete user '$username'?",
            onConfirm = {
                onDeleteUser(username)
                deleteUserConfirm = null
            },
            onDismiss = { deleteUserConfirm = null },
        )
    }

    revokeAccessConfirm?.let { (username, topic) ->
        ConfirmDialog(
            title = "Revoke access",
            text = "Are you sure you want to revoke access to '$topic' for user '$username'?",
            onConfirm = {
                onRevokeAccess(username, topic)
                revokeAccessConfirm = null
            },
            onDismiss = { revokeAccessConfirm = null },
        )
    }

    deleteTokenConfirm?.let { (username, token) ->
        ConfirmDialog(
            title = "Delete token",
            text = "Are you sure you want to delete this token for user '$username'?",
            onConfirm = {
                onDeleteToken(username, token)
                deleteTokenConfirm = null
            },
            onDismiss = { deleteTokenConfirm = null },
        )
    }
}
