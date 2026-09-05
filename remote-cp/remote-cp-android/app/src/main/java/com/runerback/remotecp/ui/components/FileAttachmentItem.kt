package com.runerback.remotecp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.runerback.remotecp.data.model.FileAttachment

sealed class FileDownloadUiState {
    object Idle : FileDownloadUiState()
    data class Downloading(val downloadId: Long, val progress: Float) : FileDownloadUiState()
    data class Downloaded(val downloadId: Long) : FileDownloadUiState()
    data class Failed(val message: String) : FileDownloadUiState()
}

@Composable
fun FileAttachmentItem(
    file: FileAttachment,
    state: FileDownloadUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = state is FileDownloadUiState.Downloading
    val isDownloaded = state is FileDownloadUiState.Downloaded
    val progress = if (state is FileDownloadUiState.Downloading) state.progress else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isDownloaded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else -> Color.Transparent
                }
            )
            .then(if (!isDownloading) Modifier.clickable(onClick = onClick) else Modifier)
            .border(
                width = 1.dp,
                color = when {
                    isDownloading || isDownloaded -> MaterialTheme.colorScheme.primary
                    else -> Color(0xFFe5e7eb).copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        if (isDownloading) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = file.name,
                color = if (isDownloaded) MaterialTheme.colorScheme.primary else Color(0xFFe5e7eb),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isDownloaded) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open file",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(18.dp)
                )
            }
        }
    }
}
