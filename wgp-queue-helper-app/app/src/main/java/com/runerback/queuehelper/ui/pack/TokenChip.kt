package com.runerback.queuehelper.ui.pack

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

@Composable
internal fun TokenChip(
    token: Token,
    imageUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)

    val (backgroundColor, contentColor, label, showThumbnail) = when (token) {
        is Token.Subject -> TokenChipStyle(
            background = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.primary,
            label = "S${token.number}",
            showThumbnail = false
        )
        is Token.Picture -> TokenChipStyle(
            background = if (imageUri != null) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            content = if (imageUri != null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            label = "P${token.number}",
            showThumbnail = imageUri != null
        )
        is Token.Audio -> TokenChipStyle(
            background = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.secondary,
            label = "A${token.number}",
            showThumbnail = false
        )
        is Token.PlainText -> error("PlainText should not be rendered as TokenChip")
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (showThumbnail) {
            val bitmap = rememberThumbnailBitmap(imageUri)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

private data class TokenChipStyle(
    val background: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val label: String,
    val showThumbnail: Boolean
)
