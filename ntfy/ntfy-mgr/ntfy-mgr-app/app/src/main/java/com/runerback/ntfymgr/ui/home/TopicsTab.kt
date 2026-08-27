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

private data class TopicGrantDialogState(
    val topic: TopicItem,
    val username: String = "",
    val readOnlyUsername: Boolean = false,
)

@Composable
fun TopicsTab(
    topics: List<TopicItem>,
    users: List<UserItem>,
    onDeleteTopic: (String) -> Unit,
    onGrantAccess: (String, String, String) -> Unit,
    onRevokeAccess: (String, String) -> Unit,
) {
    var grantDialog by remember { mutableStateOf<TopicGrantDialogState?>(null) }
    var deleteTopicConfirm by remember { mutableStateOf<String?>(null) }
    var revokeAccessConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }

    val userOptions = users.filter { it.name != "*" && it.name != "everyone" }

    Column(modifier = Modifier.padding(16.dp)) {
        LazyColumn {
            items(topics, key = { it.name }) { topic ->
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
                                    text = topic.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "${topic.accessors.size} accesses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { grantDialog = TopicGrantDialogState(topic) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Grant access",
                                )
                            }
                            IconButton(
                                onClick = { deleteTopicConfirm = topic.name },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete topic",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        if (expanded) {
                            if (userOptions.isEmpty()) {
                                Text("No users", modifier = Modifier.padding(top = 8.dp))
                            } else {
                                userOptions.forEach { user ->
                                    val accessor = topic.accessors.find { it.username == user.name }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = user.name,
                                            modifier = Modifier.weight(1f),
                                        )
                                        accessor?.let {
                                            Text(
                                                text = it.permission,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Checkbox(
                                            checked = accessor != null,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    grantDialog = TopicGrantDialogState(
                                                        topic = topic,
                                                        username = user.name,
                                                        readOnlyUsername = true,
                                                    )
                                                } else {
                                                    revokeAccessConfirm = topic.name to user.name
                                                }
                                            },
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

    grantDialog?.let { state ->
        GrantAccessDialog(
            title = "Grant access for ${state.topic.name}",
            targetLabel = "Username",
            targetValue = state.username,
            readOnlyTarget = state.readOnlyUsername,
            onConfirm = { username, permission ->
                onGrantAccess(state.topic.name, username, permission)
                grantDialog = null
            },
            onDismiss = { grantDialog = null },
        )
    }

    deleteTopicConfirm?.let { topic ->
        ConfirmDialog(
            title = "Delete topic",
            text = "Are you sure you want to delete topic '$topic'?",
            onConfirm = {
                onDeleteTopic(topic)
                deleteTopicConfirm = null
            },
            onDismiss = { deleteTopicConfirm = null },
        )
    }

    revokeAccessConfirm?.let { (topic, username) ->
        ConfirmDialog(
            title = "Revoke access",
            text = "Are you sure you want to revoke access to '$topic' for user '$username'?",
            onConfirm = {
                onRevokeAccess(topic, username)
                revokeAccessConfirm = null
            },
            onDismiss = { revokeAccessConfirm = null },
        )
    }
}
