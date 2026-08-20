package com.runerback.queuehelper.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runerback.queuehelper.data.model.Token

data class TokenFieldAvailability(
    val subjects: Boolean,
    val pictures: Boolean,
    val audio: Boolean
)

@Composable
fun TokenInputToolbar(
    onInsert: (Token) -> Unit,
    availability: TokenFieldAvailability,
    modifier: Modifier = Modifier
) {
    TokenInputToolbar(
        onInsert = onInsert,
        availableSubjects = availability.subjects,
        availablePictures = availability.pictures,
        availableAudio = availability.audio,
        modifier = modifier
    )
}

@Composable
fun TokenInputToolbar(
    onInsert: (Token) -> Unit,
    availableSubjects: Boolean,
    availablePictures: Boolean,
    availableAudio: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Text(
            text = "Insert",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        if (availableSubjects) {
            ToolbarButton(
                label = "S",
                background = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.primary,
                onClick = { onInsert(Token.Subject(1)) }
            )
        }
        if (availablePictures) {
            ToolbarButton(
                label = "P",
                background = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.error,
                onClick = { onInsert(Token.Picture(1)) }
            )
        }
        if (availableAudio) {
            ToolbarButton(
                label = "A",
                background = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.secondary,
                onClick = { onInsert(Token.Audio(1)) }
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}
