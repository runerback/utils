package com.runerback.ntfyclient.ui.send

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.ntfyclient.R
import com.runerback.ntfyclient.data.local.db.MessageEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    topic: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SendViewModel = viewModel(factory = SendViewModel.Factory(topic))
) {
    val messages by viewModel.messages.collectAsState()
    val sendState by viewModel.sendState.collectAsState()
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(sendState) {
        when (sendState) {
            is SendState.Success -> {
                text = ""
                Toast.makeText(context, R.string.message_sent, Toast.LENGTH_SHORT).show()
                viewModel.resetSendState()
            }
            is SendState.Error -> {
                val message = (sendState as SendState.Error).message
                Toast.makeText(
                    context,
                    context.getString(R.string.send_failed, message),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.resetSendState()
            }
            else -> { /* no-op */ }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(topic) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        },
        bottomBar = {
            InputBar(
                value = text,
                onValueChange = { text = it },
                expanded = expanded,
                onToggleExpand = { expanded = !expanded },
                onSend = { viewModel.send(text) },
                sending = sendState == SendState.Sending
            )
        }
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.no_messages_yet),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    OutgoingMessageItem(message = message)
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    modifier: Modifier = Modifier
) {
    val rows = if (expanded) 8 else 3

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .imePadding(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = { Text(stringResource(R.string.send_message)) },
                minLines = rows,
                maxLines = rows,
                enabled = !sending
            )

            IconButton(
                onClick = onToggleExpand,
                enabled = !sending
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse_input else R.string.expand_input
                    )
                )
            }

            IconButton(
                onClick = onSend,
                enabled = !sending && value.trim().isNotBlank()
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send)
                    )
                }
            }
        }
    }
}

@Composable
private fun OutgoingMessageItem(
    message: MessageEntity,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(12.dp)
            ) {
                Text(
                    text = formatTime(message.receivedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!message.message.isNullOrBlank()) {
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
