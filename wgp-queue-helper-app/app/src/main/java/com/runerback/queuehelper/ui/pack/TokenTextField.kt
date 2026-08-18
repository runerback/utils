package com.runerback.queuehelper.ui.pack

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.runerback.queuehelper.data.model.EditableSegment
import com.runerback.queuehelper.data.model.SubjectDefinition
import com.runerback.queuehelper.data.model.firstPictureNumber
import com.runerback.queuehelper.data.model.formatEditableSegments
import com.runerback.queuehelper.data.model.parseEditableSegments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TokenTextField(
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
    val segments = remember(value) { parseEditableSegments(value) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    var tokenToEdit by remember { mutableStateOf<Pair<Int, EditableSegment>?>(null) }

    val subjectTextColor = MaterialTheme.colorScheme.primary
    val subjectBackgroundColor = MaterialTheme.colorScheme.primaryContainer
    val pictureTextColor = MaterialTheme.colorScheme.error
    val pictureBackgroundColor = MaterialTheme.colorScheme.errorContainer
    val audioTextColor = MaterialTheme.colorScheme.secondary
    val audioBackgroundColor = MaterialTheme.colorScheme.secondaryContainer

    val annotatedValue = rememberAnnotatedSegments(segments)

    LaunchedEffect(value) {
        val formatted = formatEditableSegments(segments)
        if (textFieldValue.text != formatted) {
            textFieldValue = TextFieldValue(
                annotatedString = annotatedValue,
                selection = TextRange(formatted.length.coerceAtMost(textFieldValue.selection.start))
            )
        }
    }

    fun updateValue(newText: String, selection: TextRange) {
        val oldSegments = parseEditableSegments(textFieldValue.text)
        val (formatted, adjustedSelection) = sanitizeEdit(
            oldSegments = oldSegments,
            oldText = textFieldValue.text,
            newText = newText,
            newSelection = selection
        )
        val newSegments = parseEditableSegments(formatted)
        val annotated = buildAnnotatedSegments(
            segments = newSegments,
            subjectText = subjectTextColor,
            subjectBackground = subjectBackgroundColor,
            pictureText = pictureTextColor,
            pictureBackground = pictureBackgroundColor,
            audioText = audioTextColor,
            audioBackground = audioBackgroundColor
        )
        textFieldValue = TextFieldValue(
            annotatedString = annotated,
            selection = adjustedSelection
        )
        if (formatted != value) {
            onValueChange(formatted)
        }
    }

    fun insertToken(token: EditableSegment) {
        val position = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        val tokenString = token.toTokenString()
        val newText = textFieldValue.text.take(position) + tokenString + textFieldValue.text.drop(position)
        updateValue(newText, TextRange(position + tokenString.length))
    }

    fun deleteToken(index: Int) {
        val newSegments = mergeAdjacentText(segments.toMutableList().apply { removeAt(index) })
        val newText = formatEditableSegments(newSegments)
        updateValue(newText, TextRange(0))
    }

    fun updateToken(index: Int, newToken: EditableSegment) {
        val newSegments = segments.toMutableList().apply { set(index, newToken) }
        val newText = formatEditableSegments(newSegments)
        updateValue(newText, textFieldValue.selection)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        label()

        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                updateValue(newValue.text, newValue.selection)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            minLines = minLines,
            maxLines = maxLines,
            decorationBox = { innerTextField ->
                innerTextField()
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            if (availableSubjects && subjects.isNotEmpty()) {
                TokenInsertButton(
                    label = "Insert Subject",
                    onClick = { insertToken(EditableSegment.Subject(subjects.first().number)) }
                )
            }
            if (availablePictures && imageUris.isNotEmpty()) {
                TokenInsertButton(
                    label = "Insert Picture",
                    onClick = { insertToken(EditableSegment.Picture(1)) }
                )
            }
            if (availableAudio) {
                TokenInsertButton(
                    label = "Insert Audio",
                    onClick = { insertToken(EditableSegment.Audio(1)) }
                )
            }
        }
    }

    tokenToEdit?.let { (index, segment) ->
        when (segment) {
            is EditableSegment.Subject -> {
                TokenSubjectPickerDialog(
                    currentNumber = segment.number,
                    subjects = subjects,
                    imageUris = imageUris,
                    onDismiss = { tokenToEdit = null },
                    onSelected = { newNumber ->
                        updateToken(index, EditableSegment.Subject(newNumber))
                        tokenToEdit = null
                    }
                )
            }
            is EditableSegment.Picture -> {
                TokenPicturePickerDialog(
                    currentNumber = segment.number,
                    imageUris = imageUris,
                    onDismiss = { tokenToEdit = null },
                    onSelected = { newNumber ->
                        updateToken(index, EditableSegment.Picture(newNumber))
                        tokenToEdit = null
                    }
                )
            }
            else -> { }
        }
    }
}

@Composable
private fun rememberAnnotatedSegments(segments: List<EditableSegment>): AnnotatedString {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val error = MaterialTheme.colorScheme.error
    val errorContainer = MaterialTheme.colorScheme.errorContainer
    val secondary = MaterialTheme.colorScheme.secondary
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    return remember(
        segments,
        primary,
        primaryContainer,
        error,
        errorContainer,
        secondary,
        secondaryContainer
    ) {
        buildAnnotatedSegments(
            segments = segments,
            subjectText = primary,
            subjectBackground = primaryContainer,
            pictureText = error,
            pictureBackground = errorContainer,
            audioText = secondary,
            audioBackground = secondaryContainer
        )
    }
}

private fun buildAnnotatedSegments(
    segments: List<EditableSegment>,
    subjectText: androidx.compose.ui.graphics.Color,
    subjectBackground: androidx.compose.ui.graphics.Color,
    pictureText: androidx.compose.ui.graphics.Color,
    pictureBackground: androidx.compose.ui.graphics.Color,
    audioText: androidx.compose.ui.graphics.Color,
    audioBackground: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    segments.forEach { segment ->
        when (segment) {
            is EditableSegment.Text -> append(segment.text)
            is EditableSegment.Subject -> {
                pushStyle(
                    SpanStyle(
                        color = subjectText,
                        background = subjectBackground
                    )
                )
                append("<Subject ${segment.number}>")
                pop()
            }
            is EditableSegment.Picture -> {
                pushStyle(
                    SpanStyle(
                        color = pictureText,
                        background = pictureBackground
                    )
                )
                append("<Picture ${segment.number}>")
                pop()
            }
            is EditableSegment.Audio -> {
                pushStyle(
                    SpanStyle(
                        color = audioText,
                        background = audioBackground
                    )
                )
                append("<Audio ${segment.number}>")
                pop()
            }
        }
    }
}

private fun EditableSegment.toTokenString(): String = when (this) {
    is EditableSegment.Text -> text
    is EditableSegment.Subject -> "<Subject $number>"
    is EditableSegment.Picture -> "<Picture $number>"
    is EditableSegment.Audio -> "<Audio $number>"
}

private fun mergeAdjacentText(segments: List<EditableSegment>): List<EditableSegment> {
    if (segments.isEmpty()) return segments
    val result = mutableListOf<EditableSegment>()
    var pendingText: String? = null
    segments.forEach { segment ->
        when (segment) {
            is EditableSegment.Text -> {
                pendingText = (pendingText ?: "") + segment.text
            }
            else -> {
                pendingText?.let { result.add(EditableSegment.Text(it)) }
                pendingText = null
                result.add(segment)
            }
        }
    }
    pendingText?.let { result.add(EditableSegment.Text(it)) }
    return result
}

private fun EditableSegment.textLength(): Int = when (this) {
    is EditableSegment.Text -> text.length
    else -> toTokenString().length
}

private fun snapSelectionToTokenBoundaries(
    segments: List<EditableSegment>,
    selection: TextRange
): TextRange {
    fun snap(offset: Int, preferStart: Boolean): Int {
        var current = 0
        segments.forEach { segment ->
            val length = segment.textLength()
            val start = current
            val end = current + length
            if (offset in start..end) {
                if (segment is EditableSegment.Text || offset == start || offset == end) {
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
    oldSegments: List<EditableSegment>,
    oldText: String,
    newText: String,
    newSelection: TextRange
): Pair<String, TextRange> {
    if (oldText == newText) {
        return oldText to snapSelectionToTokenBoundaries(oldSegments, newSelection)
    }

    val prefix = oldText.commonPrefixWith(newText)
    val suffix = oldText.commonSuffixWith(newText)
    val oldStart = prefix.length
    val oldEnd = oldText.length - suffix.length
    val newStart = prefix.length
    val newEnd = newText.length - suffix.length

    if (oldStart > oldEnd || newStart > newEnd) {
        val formatted = formatEditableSegments(parseEditableSegments(newText))
        val snapped = snapSelectionToTokenBoundaries(parseEditableSegments(formatted), newSelection)
        return formatted to snapped
    }

    val oldChanged = oldText.substring(oldStart, oldEnd)
    val newChanged = newText.substring(newStart, newEnd)

    val segmentRanges = mutableListOf<Pair<Int, IntRange>>()
    var pos = 0
    oldSegments.forEachIndexed { index, segment ->
        val length = segment.textLength()
        segmentRanges.add(index to (pos until pos + length))
        pos += length
    }

    val affectedIndices = segmentRanges
        .filter { (_, range) -> range.first < oldEnd && range.last + 1 > oldStart }
        .map { it.first }

    if (affectedIndices.isEmpty()) {
        val formatted = formatEditableSegments(parseEditableSegments(newText))
        val snapped = snapSelectionToTokenBoundaries(parseEditableSegments(formatted), newSelection)
        return formatted to snapped
    }

    val firstAffected = affectedIndices.first()
    val lastAffected = affectedIndices.last()

    val affectedSegments = affectedIndices.map { oldSegments[it] }
    if (affectedSegments.all { it is EditableSegment.Text }) {
        val formatted = formatEditableSegments(parseEditableSegments(newText))
        val snapped = snapSelectionToTokenBoundaries(parseEditableSegments(formatted), newSelection)
        return formatted to snapped
    }

    val rawText = buildString {
        oldSegments.take(firstAffected).forEach { append(it.toTokenString()) }
        append(newChanged)
        oldSegments.drop(lastAffected + 1).forEach { append(it.toTokenString()) }
    }

    val formatted = formatEditableSegments(parseEditableSegments(rawText))
    val newSegments = parseEditableSegments(formatted)
    val prefixLength = oldSegments.take(firstAffected).sumOf { it.textLength() }
    val cursor = (prefixLength + newChanged.length).coerceIn(0, formatted.length)
    val snapped = snapSelectionToTokenBoundaries(newSegments, TextRange(cursor))

    return formatted to snapped
}

@Composable
private fun TokenChipRow(
    segments: List<EditableSegment>,
    subjects: List<SubjectDefinition>,
    imageUris: List<Uri>,
    onEdit: (Int, EditableSegment) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokenSegments = segments.mapIndexedNotNull { index, segment ->
        if (segment is EditableSegment.Text) null else index to segment
    }
    if (tokenSegments.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        tokenSegments.forEach { (index, segment) ->
            TokenChip(
                segment = segment,
                subjects = subjects,
                imageUris = imageUris,
                onClick = { onEdit(index, segment) },
                onDelete = { onDelete(index) }
            )
        }
    }
}

@Composable
private fun TokenChip(
    segment: EditableSegment,
    subjects: List<SubjectDefinition>,
    imageUris: List<Uri>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.dp)

    val (backgroundColor, contentColor, label, thumbnailUri) = when (segment) {
        is EditableSegment.Subject -> {
            val subject = subjects.find { it.number == segment.number }
            val pictureNumber = subject?.description?.let { firstPictureNumber(it) }
            val uri = pictureNumber?.let { imageUris.getOrNull(it - 1) }
            TokenChipStyle(
                background = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.primary,
                label = "S${segment.number}",
                thumbnail = uri
            )
        }
        is EditableSegment.Picture -> {
            val uri = imageUris.getOrNull(segment.number - 1)
            TokenChipStyle(
                background = if (uri != null) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                content = if (uri != null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                label = "P${segment.number}",
                thumbnail = uri
            )
        }
        is EditableSegment.Audio -> TokenChipStyle(
            background = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.secondary,
            label = "A${segment.number}",
            thumbnail = null
        )
        else -> error("Unsupported segment type")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = rememberThumbnailBitmap(thumbnailUri)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = label,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(shape)
                )
            } else {
                BasicText(
                    text = label,
                    style = TextStyle(
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                )
            }
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
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            )
        }
    }
}

private data class TokenChipStyle(
    val background: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val label: String,
    val thumbnail: Uri?
)

@Composable
private fun TokenInsertButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        BasicText(
            text = "+",
            style = TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp
            )
        )
        BasicText(
            text = label,
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        )
    }
}

@Composable
private fun rememberThumbnailBitmap(uri: Uri?): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            loadThumbnailBitmap(context, uri)
        }
    }.value
}

private fun loadThumbnailBitmap(context: Context, uri: Uri?): ImageBitmap? {
    if (uri == null) return null
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
internal fun TokenSubjectPickerDialog(
    currentNumber: Int,
    subjects: List<SubjectDefinition>,
    imageUris: List<Uri>,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Choose subject",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    subjects.forEach { subject ->
                        val pictureNumber = firstPictureNumber(subject.description)
                        val uri = pictureNumber?.let { imageUris.getOrNull(it - 1) }
                        val selected = subject.number == currentNumber
                        val shape = RoundedCornerShape(8.dp)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(shape)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable { onSelected(subject.number) },
                                contentAlignment = Alignment.Center
                            ) {
                                val bitmap = rememberThumbnailBitmap(uri)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Subject ${subject.number}",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    BasicText(
                                        text = "S${subject.number}",
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TokenPicturePickerDialog(
    currentNumber: Int,
    imageUris: List<Uri>,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Choose picture",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    imageUris.forEachIndexed { index, uri ->
                        val number = index + 1
                        val selected = number == currentNumber
                        val shape = RoundedCornerShape(8.dp)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(shape)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable { onSelected(number) },
                                contentAlignment = Alignment.Center
                            ) {
                                val bitmap = rememberThumbnailBitmap(uri)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Picture $number",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    BasicText(
                                        text = "P$number",
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
