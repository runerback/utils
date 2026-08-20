package com.runerback.queuehelper.ui.pack

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.Token
import com.runerback.queuehelper.data.model.export
import com.runerback.queuehelper.data.model.mergeAdjacentPlainText
import com.runerback.queuehelper.data.model.parseTokens
import com.runerback.queuehelper.ui.common.TokenFieldAvailability
import kotlin.math.roundToInt

@Composable
fun InlineTokenEditor(
    value: String,
    onValueChange: (String) -> Unit,
    subjects: List<SubjectDefinition>,
    imageUris: List<Uri>,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    fieldId: String = "",
    availableSubjects: Boolean = true,
    availablePictures: Boolean = true,
    availableAudio: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 4,
    onFocusChanged: (Boolean, TokenFieldAvailability) -> Unit = { _, _ -> },
    pendingInsertToken: Token? = null,
    onTokenInserted: () -> Unit = {}
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var editingToken by remember { mutableStateOf<Pair<Int, Token>?>(null) }
    var menuState by remember { mutableStateOf<MenuState?>(null) }
    var isFocused by remember { mutableStateOf(false) }

    val subjectTextColor = MaterialTheme.colorScheme.primary
    val subjectBackgroundColor = MaterialTheme.colorScheme.primaryContainer
    val pictureTextColor = MaterialTheme.colorScheme.error
    val pictureBackgroundColor = MaterialTheme.colorScheme.errorContainer
    val audioTextColor = MaterialTheme.colorScheme.secondary
    val audioBackgroundColor = MaterialTheme.colorScheme.secondaryContainer

    val visualTransformation = remember(
        subjectTextColor,
        subjectBackgroundColor,
        pictureTextColor,
        pictureBackgroundColor,
        audioTextColor,
        audioBackgroundColor
    ) {
        TokenVisualTransformation(
            subjectText = subjectTextColor,
            subjectBackground = subjectBackgroundColor,
            pictureText = pictureTextColor,
            pictureBackground = pictureBackgroundColor,
            audioText = audioTextColor,
            audioBackground = audioBackgroundColor
        )
    }

    val tokens = remember(value) { value.parseTokens().mergeAdjacentPlainText() }
    val visualTokens = remember(value) { TokenVisualTransformation.computeVisualRanges(value) }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length.coerceAtMost(textFieldValue.selection.start))
            )
            textLayoutResult = null
        }
    }

    fun updateValue(newText: String, selection: TextRange) {
        val oldTokens = textFieldValue.text.parseTokens().mergeAdjacentPlainText()
        val (formatted, adjustedSelection) = sanitizeEdit(
            oldTokens = oldTokens,
            oldText = textFieldValue.text,
            newText = newText,
            newSelection = selection
        )
        textFieldValue = TextFieldValue(
            text = formatted,
            selection = adjustedSelection
        )
        if (formatted != value) {
            onValueChange(formatted)
        }
    }

    fun insertToken(token: Token) {
        val position = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        val tokenString = token.toTokenString()
        val newText = textFieldValue.text.take(position) + tokenString + textFieldValue.text.drop(position)
        updateValue(newText, TextRange(position + tokenString.length))
    }

    LaunchedEffect(pendingInsertToken) {
        val token = pendingInsertToken
        if (token != null && isFocused) {
            insertToken(token)
            onTokenInserted()
        }
    }

    fun deleteToken(index: Int) {
        val newTokens = tokens.toMutableList().apply { removeAt(index) }.mergeAdjacentPlainText()
        val newText = newTokens.export()
        updateValue(newText, TextRange(0))
    }

    fun updateToken(index: Int, newToken: Token) {
        val newTokens = tokens.toMutableList().apply { set(index, newToken) }.mergeAdjacentPlainText()
        val newText = newTokens.export()
        updateValue(newText, textFieldValue.selection)
    }

    val tokenBounds = remember(textLayoutResult, visualTokens) {
        textLayoutResult?.let { computeTokenBounds(it, visualTokens) } ?: emptyList()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        label()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    updateValue(newValue.text, newValue.selection)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        val availability = TokenFieldAvailability(
                            subjects = availableSubjects && subjects.isNotEmpty(),
                            pictures = availablePictures && imageUris.isNotEmpty(),
                            audio = availableAudio
                        )
                        onFocusChanged(state.isFocused, availability)
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                minLines = minLines,
                maxLines = Int.MAX_VALUE,
                visualTransformation = visualTransformation,
                onTextLayout = { result ->
                    textLayoutResult = result
                },
                decorationBox = { innerTextField ->
                    innerTextField()
                }
            )

            TokenOverlay(
                bounds = tokenBounds,
                imageUriFor = { imageUris.getOrNull(it.number - 1) },
                onTokenClick = { index, token ->
                    val bounds = tokenBounds.find { it.index == index && it.token == token }?.bounds
                    menuState = MenuState(index, token, bounds)
                },
                modifier = Modifier.fillMaxSize()
            )

            menuState?.let { state ->
                Popup(
                    offset = IntOffset(
                        x = state.bounds?.left?.roundToInt() ?: 0,
                        y = state.bounds?.bottom?.roundToInt() ?: 0
                    ),
                    onDismissRequest = { menuState = null }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            TextButton(
                                onClick = {
                                    deleteToken(state.index)
                                    menuState = null
                                },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
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
                        updateToken(index, Token.Subject(newNumber))
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
                        updateToken(index, Token.Picture(newNumber))
                        editingToken = null
                    }
                )
            }
            else -> { editingToken = null }
        }
    }
}

private data class MenuState(
    val index: Int,
    val token: Token,
    val bounds: Rect?
)

private fun Token.toTokenString(): String = when (this) {
    is Token.PlainText -> text
    is Token.Subject -> "<Subject $number>"
    is Token.Picture -> "<Picture $number>"
    is Token.Audio -> "<Audio $number>"
}

private fun Token.textLength(): Int = when (this) {
    is Token.PlainText -> text.length
    is Token.Subject -> "<Subject $number>".length
    is Token.Picture -> "<Picture $number>".length
    is Token.Audio -> "<Audio $number>".length
}

private fun snapSelectionToTokenBoundaries(
    tokens: List<Token>,
    selection: TextRange
): TextRange {
    fun snap(offset: Int, preferStart: Boolean): Int {
        var current = 0
        tokens.forEach { token ->
            val length = token.textLength()
            val start = current
            val end = current + length
            if (offset in start..end) {
                if (token is Token.PlainText || offset == start || offset == end) {
                    return offset
                }
                val toStart = offset - start
                val toEnd = end - offset
                return if (preferStart) {
                    start
                } else {
                    if (toStart < toEnd) start else end
                }
            }
            current = end
        }
        return offset
    }

    val start = snap(selection.start, preferStart = true)
    val end = snap(selection.end, preferStart = false)
    return TextRange(start, end)
}

private fun sanitizeEdit(
    oldTokens: List<Token>,
    oldText: String,
    newText: String,
    newSelection: TextRange
): Pair<String, TextRange> {
    if (oldText == newText) {
        return oldText to snapSelectionToTokenBoundaries(oldTokens, newSelection)
    }

    val prefix = oldText.commonPrefixWith(newText)
    val suffix = oldText.commonSuffixWith(newText)
    val oldStart = prefix.length
    val oldEnd = oldText.length - suffix.length
    val newStart = prefix.length
    val newEnd = newText.length - suffix.length

    if (oldStart > oldEnd || newStart > newEnd) {
        val formatted = newText.parseTokens().mergeAdjacentPlainText().export()
        val snapped = snapSelectionToTokenBoundaries(formatted.parseTokens().mergeAdjacentPlainText(), newSelection)
        return formatted to snapped
    }

    val oldChanged = oldText.substring(oldStart, oldEnd)
    val newChanged = newText.substring(newStart, newEnd)

    val tokenRanges = mutableListOf<Pair<Int, IntRange>>()
    var pos = 0
    oldTokens.forEachIndexed { index, token ->
        val length = token.textLength()
        tokenRanges.add(index to (pos until pos + length))
        pos += length
    }

    val affectedIndices = tokenRanges
        .filter { (_, range) -> range.first < oldEnd && range.last + 1 > oldStart }
        .map { it.first }

    if (affectedIndices.isEmpty()) {
        val formatted = newText.parseTokens().mergeAdjacentPlainText().export()
        val snapped = snapSelectionToTokenBoundaries(formatted.parseTokens().mergeAdjacentPlainText(), newSelection)
        return formatted to snapped
    }

    val firstAffected = affectedIndices.first()
    val lastAffected = affectedIndices.last()

    val affectedTokens = affectedIndices.map { oldTokens[it] }
    if (affectedTokens.all { it is Token.PlainText }) {
        val formatted = newText.parseTokens().mergeAdjacentPlainText().export()
        val snapped = snapSelectionToTokenBoundaries(formatted.parseTokens().mergeAdjacentPlainText(), newSelection)
        return formatted to snapped
    }

    val rawText = buildString {
        oldTokens.take(firstAffected).forEach { append(it.toTokenString()) }
        append(newChanged)
        oldTokens.drop(lastAffected + 1).forEach { append(it.toTokenString()) }
    }

    val formatted = rawText.parseTokens().mergeAdjacentPlainText().export()
    val newTokens = formatted.parseTokens().mergeAdjacentPlainText()
    val prefixLength = oldTokens.take(firstAffected).sumOf { it.textLength() }
    val cursor = (prefixLength + newChanged.length).coerceIn(0, formatted.length)
    val snapped = snapSelectionToTokenBoundaries(newTokens, TextRange(cursor))

    return formatted to snapped
}
