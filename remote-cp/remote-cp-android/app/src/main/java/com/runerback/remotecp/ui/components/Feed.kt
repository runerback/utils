package com.runerback.remotecp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.runerback.remotecp.data.model.FileAttachment
import com.runerback.remotecp.data.model.ImageAttachment
import com.runerback.remotecp.data.model.Message
import com.runerback.remotecp.data.model.VideoAttachment

@Composable
fun Feed(
    messages: List<Message>,
    isConnected: Boolean,
    isLoading: Boolean,
    backendUrl: String,
    error: String?,
    markdownMode: Map<String, Boolean> = emptyMap(),
    fileDownloadStates: Map<String, FileDownloadUiState> = emptyMap(),
    onToggleMarkdown: (String) -> Unit = {},
    onStatus: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit = {},
    onImageClick: (ImageAttachment) -> Unit = {},
    onVideoClick: (VideoAttachment) -> Unit = {},
    onFileClick: (FileAttachment) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Room activity",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isLoading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                    )
                }
                ConnectionPill(isConnected = isConnected)
            }
        }

        Text(
            text = backendUrl,
            color = Color(0xFF64748b),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (error != null) {
                    Text(
                        text = "Could not load messages",
                        color = Color(0xFFf87171),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color(0xFF94a3b8),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Check server settings and make sure the URL is correct.",
                        color = Color(0xFF64748b),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onOpenSettings) {
                        Text("Open Settings")
                    }
                } else {
                    Text(
                        text = "The room is empty right now. Send the first message.",
                        color = Color(0xFF94a3b8),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    val isMarkdown = markdownMode[message.id] ?: false
                    MessageCard(
                        message = message,
                        backendUrl = backendUrl,
                        isMarkdown = isMarkdown,
                        fileDownloadStates = fileDownloadStates,
                        onToggleMarkdown = { onToggleMarkdown(message.id) },
                        onStatus = onStatus,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onFileClick = onFileClick
                    )
                }
            }
        }
    }
}
