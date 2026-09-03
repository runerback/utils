package com.runerback.remotecp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.runerback.remotecp.data.FileTypes

@Composable
fun Composer(
    isLoading: Boolean,
    onSendText: (String) -> Unit,
    onSendMedia: (images: List<Uri>?, videos: List<Uri>?, files: List<Uri>?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onSendMedia(uris, null, null)
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onSendMedia(null, uris, null)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onSendMedia(null, null, uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Remote Copy/Paste",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onOpenLogs) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Logs")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                shape = RoundedCornerShape(14.dp)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val item = clip.getItemAt(0)
                            val clipText = item.text?.toString() ?: ""
                            if (clipText.isNotBlank()) {
                                onSendText(clipText)
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                }
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSendText(text)
                            text = ""
                        }
                    },
                    enabled = !isLoading && text.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { imagePicker.launch(FileTypes.IMAGE_MIME) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send pictures")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { videoPicker.launch(FileTypes.VIDEO_MIME) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send videos")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { filePicker.launch(FileTypes.FILE_MIME_ARRAY) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send files")
                }
            }
        }
    }
}
