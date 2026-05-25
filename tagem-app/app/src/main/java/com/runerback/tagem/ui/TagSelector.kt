package com.runerback.tagem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.tagem.data.TagEntity

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TagSelector(
    tags: List<TagEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTagClick: (TagEntity) -> Unit,
    modifier: Modifier = Modifier,
    selectedTagIds: Set<Long> = emptySet(),
    emptyMessage: String = "No tags",
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search tags") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (tags.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.heightIn(max = 200.dp),
            ) {
                items(tags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = { onTagClick(tag) },
                        label = { Text(tag.name) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
