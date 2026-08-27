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
import com.runerback.ntfymgr.data.remote.TopicItem
import com.runerback.ntfymgr.data.remote.UserItem
import com.runerback.ntfymgr.ui.components.PromptDialog

@Composable
fun TopicsTab(
    topics: List<TopicItem>,
    users: List<UserItem>,
    onCreateTopic: (String, String, String) -> Unit,
    onDeleteTopic: (String) -> Unit,
    onGrantAccess: (String, String, String) -> Unit,
    onRevokeAccess: (String, String) -> Unit,
) {
    var newTopic by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf("read-write") }
    var grantDialogTopic by remember { mutableStateOf<TopicItem?>(null) }

    val userOptions = users.filter { it.name != "*" && it.name != "everyone" }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newTopic,
                onValueChange = { newTopic = it },
                label = { Text("Topic") },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    if (selectedUser.isNotBlank()) {
                        onCreateTopic(newTopic, selectedUser, permission)
                        newTopic = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Create")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = selectedUser,
                onValueChange = { selectedUser = it },
                label = { Text("Initial user") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = permission,
                onValueChange = { permission = it },
                label = { Text("Permission") },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }

        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(topics, key = { it.name }) { topic ->
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
                                text = topic.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { grantDialogTopic = topic }) {
                                Text("Grant")
                            }
                            Button(
                                onClick = { onDeleteTopic(topic.name) },
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text("Delete")
                            }
                        }

                        if (topic.accessors.isEmpty()) {
                            Text("No accessors", modifier = Modifier.padding(top = 8.dp))
                        } else {
                            topic.accessors.forEach { accessor ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${accessor.username}: ${accessor.permission}",
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(
                                        onClick = { onRevokeAccess(topic.name, accessor.username) },
                                    ) {
                                        Text("Revoke")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    grantDialogTopic?.let { topic ->
        PromptDialog(
            title = "Grant access for ${topic.name}",
            fields = listOf(
                "Username" to "",
                "Permission (read-write/read-only/write-only)" to "read-write",
            ),
            onConfirm = { values ->
                onGrantAccess(topic.name, values[0], values[1])
                grantDialogTopic = null
            },
            onDismiss = { grantDialogTopic = null },
        )
    }
}
