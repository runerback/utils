package com.runerback.remotecp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.runerback.remotecp.R
import com.runerback.remotecp.data.model.FileAttachment
import com.runerback.remotecp.data.model.ImageAttachment
import com.runerback.remotecp.data.model.Message
import com.runerback.remotecp.data.model.VideoAttachment

private fun deviceIcon(deviceType: String): Int {
    return when (deviceType) {
        "Computer" -> R.drawable.ic_computer
        "Tablet" -> R.drawable.ic_tablet
        else -> R.drawable.ic_phone
    }
}

@Composable
fun MessageCard(
    message: Message,
    backendUrl: String,
    isMarkdown: Boolean = false,
    fileDownloadStates: Map<String, FileDownloadUiState> = emptyMap(),
    onToggleMarkdown: () -> Unit,
    onStatus: (String) -> Unit,
    onImageClick: (ImageAttachment) -> Unit = {},
    onVideoClick: (VideoAttachment) -> Unit = {},
    onFileClick: (FileAttachment) -> Unit = {}
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = deviceIcon(message.deviceType)),
                        contentDescription = null,
                        tint = Color(0xFFbae6fd),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = message.deviceType,
                        color = Color(0xFFbae6fd),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .background(
                                Color(0xFF38bdf8).copy(alpha = 0.1f),
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = message.clientTimestamp,
                    color = Color(0xFF94a3b8),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (message.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("text", message.text))
                            onStatus("Text copied.")
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFe5e7eb)
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy text")
                    }
                    OutlinedButton(
                        onClick = onToggleMarkdown,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFe5e7eb)
                        )
                    ) {
                        Icon(
                            imageVector = if (isMarkdown) Icons.Default.TextFields else Icons.Default.Code,
                            contentDescription = if (isMarkdown) "Render as plain text" else "Render as markdown"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isMarkdown) "Plain" else "Markdown")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    if (isMarkdown) {
                        MarkdownText(
                            markdown = message.text,
                            color = Color(0xFFe5e7eb),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = message.text,
                            color = Color(0xFFe5e7eb),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            message.images.forEach { image ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clickable { onImageClick(image) }
                ) {
                    AsyncImage(
                        model = "$backendUrl${image.url}",
                        contentDescription = image.name,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = image.name,
                    color = Color(0xFFe5e7eb),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            message.videos.forEach { video ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onVideoClick(video) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = Color(0xFFe5e7eb),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = video.name,
                        color = Color(0xFFe5e7eb),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            message.files.forEach { file ->
                Spacer(modifier = Modifier.height(8.dp))
                val fileUrl = backendUrl + file.downloadUrl
                FileAttachmentItem(
                    file = file,
                    state = fileDownloadStates[fileUrl] ?: FileDownloadUiState.Idle,
                    onClick = { onFileClick(file) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
