package com.runerback.queuehelper.ui.pack

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.Token
import com.runerback.queuehelper.data.model.export
import com.runerback.queuehelper.data.model.mergeAdjacentPlainText
import com.runerback.queuehelper.data.model.parseTokens
import com.runerback.queuehelper.data.model.removeTokenAt
import com.runerback.queuehelper.data.model.splitAndInsert

@Composable
fun InlineTokenEditor(
    value: String,
    onValueChange: (String) -> Unit,
    subjects: List<SubjectDefinition>,
    imageUris: List<Uri>,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    availableSubjects: Boolean = true,
    availablePictures: Boolean = true,
    availableAudio: Boolean = false,
    minLines: Int = 2,
    maxLines: Int = 4
) {
    var tokens by remember(value) { mutableStateOf(value.parseTokens().mergeAdjacentPlainText().ensureTrailingPlainText()) }
    var focusTarget by remember { mutableStateOf<FocusTarget?>(null) }
    var fieldValues by remember { mutableStateOf(emptyMap<Int, TextFieldValue>()) }
    var editingToken by remember { mutableStateOf<Pair<Int, Token>?>(null) }
    var focusedFieldIndex by remember { mutableStateOf(-1) }

    val focusRequesters = remember(tokens.size) { List(tokens.size) { FocusRequester() } }

    LaunchedEffect(value) {
        val parsed = value.parseTokens().mergeAdjacentPlainText().ensureTrailingPlainText()
        if (parsed != tokens) {
            tokens = parsed
        }
    }

    LaunchedEffect(tokens) {
        fieldValues = tokens.mapIndexed { index, token ->
            val existing = fieldValues[index]
            val valueForToken = when (token) {
                is Token.PlainText -> {
                    if (existing != null && existing.text == token.text) {
                        existing
                    } else {
                        TextFieldValue(token.text)
                    }
                }
                else -> TextFieldValue("")
            }
            index to valueForToken
        }.toMap()

        focusTarget?.let { target ->
            if (target.index in tokens.indices) {
                focusRequesters[target.index].requestFocus()
                fieldValues = fieldValues.toMutableMap().apply {
                    put(target.index, TextFieldValue(get(target.index)?.text ?: "", target.selection))
                }
            }
            focusTarget = null
        }
    }

    fun updateTokens(newTokens: List<Token>, nextFocus: FocusTarget?) {
        tokens = newTokens
        focusTarget = nextFocus
        onValueChange(newTokens.export())
    }

    fun onTextChange(index: Int, newValue: TextFieldValue) {
        fieldValues = fieldValues.toMutableMap().apply { put(index, newValue) }
        val current = tokens[index]
        if (current is Token.PlainText && current.text != newValue.text) {
            val newTokens = tokens.toMutableList().apply { set(index, Token.PlainText(newValue.text)) }
            updateTokens(newTokens, null)
        }
    }

    fun onDeleteToken(index: Int) {
        val newTokens = tokens.removeTokenAt(index)
        val targetIndex = findPlainTextIndexNear(newTokens, index)
        updateTokens(
            newTokens,
            if (targetIndex != -1) FocusTarget(targetIndex, TextRange(newTokens[targetIndex].textLength())) else null
        )
    }

    fun onBackspaceAtStart(index: Int): Boolean {
        if (index <= 0) return false
        val newTokens = tokens.removeTokenAt(index - 1)
        val targetIndex = findPlainTextIndexNear(newTokens, index - 1)
        updateTokens(
            newTokens,
            if (targetIndex != -1) FocusTarget(targetIndex, TextRange(newTokens[targetIndex].textLength())) else null
        )
        return true
    }

    fun onInsertToken(token: Token) {
        val focusIndex = if (focusedFieldIndex in tokens.indices && tokens[focusedFieldIndex] is Token.PlainText) {
            focusedFieldIndex
        } else {
            findLastPlainTextIndex(tokens)
        }

        if (focusIndex == -1 || tokens[focusIndex] !is Token.PlainText) {
            val newTokens = tokens.toMutableList().apply {
                add(Token.PlainText(""))
                add(token)
                add(Token.PlainText(""))
            }
            updateTokens(newTokens, FocusTarget(newTokens.size - 1, TextRange.Zero))
            return
        }

        val cursor = fieldValues[focusIndex]?.selection?.start ?: 0
        val newTokens = tokens.toMutableList()
        newTokens.splitAndInsert(focusIndex, cursor, token)
        updateTokens(newTokens, FocusTarget(focusIndex + 2, TextRange.Zero))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        label()

        InlineTokenLayout(
            slots = tokens.mapIndexed { index, token ->
                when (token) {
                    is Token.PlainText -> InlineSlot.Text(index)
                    else -> InlineSlot.Chip(index)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalSpacing = 4.dp,
            minLines = minLines,
            maxLines = maxLines
        ) { slot ->
            val index = slot.index
            val token = tokens[index]
            TokenComponent(
                token = token,
                imageUri = if (token is Token.Picture) imageUris.getOrNull(token.number - 1) else null,
                textValue = fieldValues[index] ?: TextFieldValue(""),
                onTextChange = { newValue -> onTextChange(index, newValue) },
                onClick = { editingToken = index to token },
                onDelete = { onDeleteToken(index) },
                focusRequester = focusRequesters[index],
                shouldRequestFocus = focusTarget?.index == index,
                onFocusChanged = { focusTarget = null },
                onFocused = { focusedFieldIndex = index },
                onBackspaceAtStart = { onBackspaceAtStart(index) }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            if (availableSubjects && subjects.isNotEmpty()) {
                TokenInsertButton(
                    label = "Insert Subject",
                    onClick = { onInsertToken(Token.Subject(subjects.first().number)) }
                )
            }
            if (availablePictures && imageUris.isNotEmpty()) {
                TokenInsertButton(
                    label = "Insert Picture",
                    onClick = { onInsertToken(Token.Picture(1)) }
                )
            }
            if (availableAudio) {
                TokenInsertButton(
                    label = "Insert Audio",
                    onClick = { onInsertToken(Token.Audio(1)) }
                )
            }
        }
    }

    editingToken?.let { (index, token) ->
        when (token) {
            is Token.Subject -> {
                TokenSubjectPickerDialog(
                    currentNumber = token.number,
                    subjects = subjects,
                    imageUris = imageUris,
                    onDismiss = { editingToken = null },
                    onSelected = { newNumber ->
                        updateTokens(
                            tokens.toMutableList().apply { set(index, Token.Subject(newNumber)) },
                            FocusTarget(index, TextRange.Zero)
                        )
                        editingToken = null
                    }
                )
            }
            is Token.Picture -> {
                TokenPicturePickerDialog(
                    currentNumber = token.number,
                    imageUris = imageUris,
                    onDismiss = { editingToken = null },
                    onSelected = { newNumber ->
                        updateTokens(
                            tokens.toMutableList().apply { set(index, Token.Picture(newNumber)) },
                            FocusTarget(index, TextRange.Zero)
                        )
                        editingToken = null
                    }
                )
            }
            else -> { editingToken = null }
        }
    }
}

private data class FocusTarget(val index: Int, val selection: TextRange)

private fun findPlainTextIndexNear(tokens: List<Token>, index: Int): Int {
    for (i in (index - 1) downTo 0) {
        if (tokens[i] is Token.PlainText) return i
    }
    for (i in index until tokens.size) {
        if (tokens[i] is Token.PlainText) return i
    }
    return -1
}

private fun findLastPlainTextIndex(tokens: List<Token>): Int {
    for (i in tokens.size - 1 downTo 0) {
        if (tokens[i] is Token.PlainText) return i
    }
    return -1
}

private fun Token.textLength(): Int = when (this) {
    is Token.PlainText -> text.length
    is Token.Subject -> "<Subject $number>".length
    is Token.Picture -> "<Picture $number>".length
    is Token.Audio -> "<Audio $number>".length
}

private fun List<Token>.ensureTrailingPlainText(): List<Token> {
    if (isEmpty() || last() !is Token.PlainText) {
        return this + Token.PlainText("")
    }
    return this
}
