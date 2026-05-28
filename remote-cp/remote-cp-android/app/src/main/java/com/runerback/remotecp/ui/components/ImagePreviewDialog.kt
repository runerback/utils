package com.runerback.remotecp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.runerback.remotecp.data.model.ImageAttachment
import com.runerback.remotecp.util.saveToDownloads

@Composable
fun ImagePreviewDialog(
    image: ImageAttachment,
    backendUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = "$backendUrl${image.url}"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = image.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() },
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                OutlinedButton(
                    onClick = {
                        context.saveToDownloads(imageUrl, image.name)
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text("Save", color = Color.White)
                }
            }
        }
    }
}
