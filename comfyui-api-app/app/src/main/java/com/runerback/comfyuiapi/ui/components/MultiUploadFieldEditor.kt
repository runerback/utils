package com.runerback.comfyuiapi.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun MultiUploadFieldEditor(
    label: String,
    mimeType: String,
    selectedUris: List<Uri>,
    onUrisSelected: (List<Uri>) -> Unit,
    onRemoveUri: (Uri) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    headerAction: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onUrisSelected(uris)
            }
        }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            headerAction?.invoke()
        }

        Button(
            onClick = { launcher.launch(mimeType) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose files")
        }

        if (selectedUris.isNotEmpty()) {
            Text(
                text = "${selectedUris.size} selected",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedUris, key = { it.toString() }) { uri ->
                    val bitmap = remember(uri) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.asImageBitmap()
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }

                    Box {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = uri.lastPathSegment,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uri.lastPathSegment?.take(2) ?: "?",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        IconButton(
                            onClick = { onRemoveUri(uri) },
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onClear,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Clear")
            }
        }
    }
}
