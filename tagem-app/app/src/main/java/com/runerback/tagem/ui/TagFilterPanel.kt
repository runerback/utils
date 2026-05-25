package com.runerback.tagem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.tagem.data.TagEntity

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TagFilterPanel(
    tags: List<TagEntity>,
    selectedTagId: Long?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectTag: (Long?) -> Unit,
    showGifsOnly: Boolean,
    onToggleGifsOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Filter",
            style = MaterialTheme.typography.titleMedium,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedTagId == null && !showGifsOnly,
                onClick = {
                    onSelectTag(null)
                    if (showGifsOnly) onToggleGifsOnly()
                },
                label = { Text("All Photos") },
            )
            FilterChip(
                selected = showGifsOnly,
                onClick = onToggleGifsOnly,
                label = { Text("GIFs Only") },
            )
        }

        TagSelector(
            tags = tags,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onTagClick = { tag ->
                onSelectTag(if (selectedTagId == tag.id) null else tag.id)
            },
            selectedTagIds = selectedTagId?.let { setOf(it) } ?: emptySet(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
