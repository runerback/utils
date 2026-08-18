package com.runerback.queuehelper.ui.pack

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runerback.queuehelper.data.model.Token

@Composable
internal fun TokenComponent(
    token: Token,
    imageUri: Uri?,
    textValue: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    focusRequester: FocusRequester,
    shouldRequestFocus: Boolean,
    onFocusChanged: () -> Unit,
    onFocused: () -> Unit,
    onBackspaceAtStart: () -> Boolean,
    modifier: Modifier = Modifier
) {
    when (token) {
        is Token.PlainText -> PlainTextTokenField(
            value = textValue,
            onValueChange = onTextChange,
            focusRequester = focusRequester,
            shouldRequestFocus = shouldRequestFocus,
            onFocusChanged = onFocusChanged,
            onFocused = onFocused,
            onBackspaceAtStart = onBackspaceAtStart,
            modifier = modifier
        )
        is Token.Picture -> PictureTokenChip(
            number = token.number,
            imageUri = imageUri,
            onClick = onClick,
            onDelete = onDelete,
            modifier = modifier
        )
        is Token.Subject -> TypedTokenChip(
            label = "S${token.number}",
            background = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.primary,
            onClick = onClick,
            onDelete = onDelete,
            modifier = modifier
        )
        is Token.Audio -> TypedTokenChip(
            label = "A${token.number}",
            background = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.secondary,
            onClick = onClick,
            onDelete = onDelete,
            modifier = modifier
        )
    }
}

@Composable
private fun PlainTextTokenField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    shouldRequestFocus: Boolean,
    onFocusChanged: () -> Unit,
    onFocused: () -> Unit,
    onBackspaceAtStart: () -> Boolean,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus) {
            focusRequester.requestFocus()
            onFocusChanged()
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .widthIn(min = 0.dp)
            .padding(horizontal = 4.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    onFocused()
                }
                onFocusChanged()
            }
            .onKeyEvent { event ->
                if (event.key == Key.Backspace && event.type == KeyEventType.KeyUp && value.selection.start == 0) {
                    onBackspaceAtStart()
                } else {
                    false
                }
            },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        decorationBox = { innerTextField ->
            innerTextField()
        }
    )
}

@Composable
private fun TypedTokenChip(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = label,
                style = TextStyle(
                    color = content,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "×",
                style = TextStyle(
                    color = content,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            )
        }
    }
}
