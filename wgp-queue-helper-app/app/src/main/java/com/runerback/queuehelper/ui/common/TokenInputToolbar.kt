package com.runerback.queuehelper.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.runerback.queuehelper.data.model.Token
import com.runerback.queuehelper.ui.pack.MAX_PICTURE_SLOTS

data class TokenFieldAvailability(
    val subjects: Boolean,
    val pictures: Boolean,
    val audio: Boolean
)

@Composable
fun TokenInputToolbar(
    onInsert: (Token) -> Unit,
    availability: TokenFieldAvailability,
    modifier: Modifier = Modifier,
    subjectCount: Int = 0,
    pictureCount: Int = MAX_PICTURE_SLOTS
) {
    TokenInputToolbar(
        onInsert = onInsert,
        availableSubjects = availability.subjects,
        availablePictures = availability.pictures,
        availableAudio = availability.audio,
        modifier = modifier,
        subjectCount = subjectCount,
        pictureCount = pictureCount
    )
}

@Composable
fun TokenInputToolbar(
    onInsert: (Token) -> Unit,
    availableSubjects: Boolean,
    availablePictures: Boolean,
    availableAudio: Boolean,
    modifier: Modifier = Modifier,
    subjectCount: Int = 0,
    pictureCount: Int = MAX_PICTURE_SLOTS
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
            SelectorToolbarButton(
                label = "S",
                count = subjectCount,
                background = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.primary,
                onInsert = { onInsert(Token.Subject(it)) }
            )
        }
        if (availablePictures) {
            SelectorToolbarButton(
                label = "P",
                count = pictureCount,
                background = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.error,
                onInsert = { onInsert(Token.Picture(it)) }
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
private fun SelectorToolbarButton(
    label: String,
    count: Int,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onInsert: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPopup by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ToolbarButton(
            label = label,
            background = background,
            content = content,
            onClick = { onInsert(1) },
            onLongClick = if (count > 1) {
                { showPopup = true }
            } else {
                null
            }
        )

        if (showPopup) {
            TokenSelectorPopup(
                count = count,
                labelPrefix = label,
                background = background,
                content = content,
                onSelected = {
                    onInsert(it)
                    showPopup = false
                },
                onDismiss = { showPopup = false }
            )
        }
    }
}

@Composable
private fun TokenSelectorPopup(
    count: Int,
    labelPrefix: String,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val itemSize = 32.dp
    val spacing = 4.dp
    val padding = 4.dp
    val margin = 4.dp

    val popupWidthPx = with(density) {
        (padding * 2 + itemSize * count + spacing * (count - 1)).roundToPx()
    }
    val popupHeightPx = with(density) {
        (padding * 2 + itemSize).roundToPx()
    }
    val buttonWidthPx = with(density) { itemSize.roundToPx() }
    val marginPx = with(density) { margin.roundToPx() }

    Popup(
        offset = androidx.compose.ui.unit.IntOffset(
            x = (buttonWidthPx - popupWidthPx) / 2,
            y = -popupHeightPx - marginPx
        ),
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.padding(padding)
            ) {
                repeat(count) { index ->
                    val number = index + 1
                    Box(
                        modifier = Modifier
                            .size(itemSize)
                            .clip(RoundedCornerShape(6.dp))
                            .background(background)
                            .combinedClickable(
                                onClick = { onSelected(number) },
                                onLongClick = null
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$labelPrefix$number",
                            color = content,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(shape)
            .background(background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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
