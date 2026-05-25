package com.runerback.tagem.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.runerback.tagem.data.ImageStore
import com.runerback.tagem.data.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TagEditorDialog(
    uri: Uri,
    isGif: Boolean,
    tags: List<TagEntity>,
    allTags: List<TagEntity>,
    onDismiss: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (Long) -> Unit,
) {
    val context = LocalContext.current
    val thumbnail by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            ImageStore.loadThumbnail(context, uri, 512)
        }
    }
    var searchText by remember { mutableStateOf("") }
    var newTagText by remember { mutableStateOf("") }
    val currentTagIds = tags.map { it.id }.toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        title = { Text("Tag This Photo") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isGif) {
                    AnimatedGifImage(
                        uri = uri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                    )
                } else {
                    thumbnail?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                Text("Current tags:", style = MaterialTheme.typography.labelMedium)

                if (tags.isEmpty()) {
                    Text("No tags yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(tags, key = { it.id }) { tag ->
                            TagChip(
                                tag = tag,
                                onRemove = { onRemoveTag(tag.id) },
                            )
                        }
                    }
                }

                Text("Add from existing:", style = MaterialTheme.typography.labelMedium)

                Box(modifier = Modifier.height(160.dp)) {
                    TagSelector(
                        tags = allTags.filter { it.id !in currentTagIds },
                        searchQuery = searchText,
                        onSearchQueryChange = { searchText = it },
                        onTagClick = { tag ->
                            onAddTag(tag.name)
                            searchText = ""
                        },
                        emptyMessage = if (searchText.isBlank()) "All tags already added" else "No matching tags",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("Or create new") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                if (newTagText.isNotBlank()) {
                                    onAddTag(newTagText)
                                    newTagText = ""
                                }
                            },
                            enabled = newTagText.isNotBlank(),
                        ) {
                            Text("Add")
                        }
                    },
                )
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TagChip(
    tag: TagEntity,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.InputChip(
            selected = false,
            onClick = onRemove,
            label = { Text(tag.name) },
            trailingIcon = {
                Text("X", modifier = Modifier.padding(horizontal = 4.dp))
            },
        )
    }
}
