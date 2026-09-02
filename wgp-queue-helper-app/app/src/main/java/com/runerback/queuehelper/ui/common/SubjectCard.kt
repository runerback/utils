package com.runerback.queuehelper.ui.common

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runerback.queuehelper.data.model.Token
import com.runerback.queuehelper.ui.pack.InlineTokenEditor

@Composable
fun SubjectCard(
    number: Int,
    description: String,
    onRemove: () -> Unit,
    onUpdateDescription: (String) -> Unit,
    fieldId: String,
    onFocusChanged: (Boolean, TokenFieldAvailability) -> Unit,
    pendingInsertToken: Token?,
    onTokenInserted: () -> Unit,
    modifier: Modifier = Modifier,
    imageUris: List<Uri> = emptyList()
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Subject $number",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove subject"
                    )
                }
            }

            InlineTokenEditor(
                value = description,
                onValueChange = onUpdateDescription,
                subjects = emptyList(),
                imageUris = imageUris,
                onFocusChanged = onFocusChanged,
                pendingInsertToken = pendingInsertToken,
                onTokenInserted = onTokenInserted,
                availableSubjects = false,
                availablePictures = true,
                availableAudio = false,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6
            )
        }
    }
}
