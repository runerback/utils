package com.runerback.translator.reader.text

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.widget.TextView
import com.runerback.translator.reader.IndexedOffset
import com.runerback.translator.util.LogManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun TextReaderScreen(
    content: String,
    initialPage: Int = 0,
    fontSizeSp: Float = 18f,
    lineSpacingMultiplier: Float = 1.3f,
    selectWordRequest: IndexedOffset? = null,
    clearSelectionSignal: Int = 0,
    onSelectWordHandled: (Int) -> Unit = {},
    onPageChange: (Int, Int) -> Unit = { _, _ -> },
    onTotalPages: (Int) -> Unit = {},
    onSelectionChanged: (String?, anchor: Rect?) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var pages by remember { mutableStateOf(listOf("")) }
    var currentPage by remember(initialPage) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    // Margin around the text. The TextView carries it as internal padding, so
    // every coordinate conversion below happens inside this screen.
    val marginPx = remember(density, fontSizeSp) { textMarginPx(context, fontSizeSp) }
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var textView by remember { mutableStateOf<TextView?>(null) }
    var textViewWindowOffset by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(Unit) {
        onDispose { textView = null }
    }

    LaunchedEffect(selectWordRequest) {
        val request = selectWordRequest ?: return@LaunchedEffect
        textView?.let { selectWordAt(it, request.offset.x, request.offset.y) }
        onSelectWordHandled(request.id)
    }

    LaunchedEffect(clearSelectionSignal) {
        if (clearSelectionSignal > 0) {
            val spannable = textView?.text as? Spannable ?: return@LaunchedEffect
            Selection.removeSelection(spannable)
        }
    }

    LaunchedEffect(content, containerSize, marginPx) {
        if (content.isEmpty()) {
            LogManager.d("TextReaderScreen", "empty content")
            return@LaunchedEffect
        }
        if (containerSize.width <= 0 || containerSize.height <= 0) {
            LogManager.d("TextReaderScreen", "waiting for layout size=$containerSize")
            return@LaunchedEffect
        }
        val computed = computePages(
            context = context,
            text = content,
            width = containerSize.width.toInt(),
            height = containerSize.height.toInt(),
            fontSizeSp = fontSizeSp,
            lineSpacingMultiplier = lineSpacingMultiplier,
            paddingPx = marginPx,
        )
        pages = computed
        val total = computed.size.coerceAtLeast(1)
        LogManager.d(
            "TextReaderScreen",
            "computed pages=$total size=$containerSize contentLength=${content.length}",
        )
        onTotalPages(total)
        val safePage = currentPage.coerceIn(0, computed.size - 1)
        if (safePage != currentPage) {
            currentPage = safePage
        }
        onPageChange(currentPage, total)
    }

    LaunchedEffect(initialPage) {
        if (initialPage in pages.indices && initialPage != currentPage) {
            currentPage = initialPage
            onPageChange(currentPage, pages.size.coerceAtLeast(1))
        }
    }

    LaunchedEffect(selection, textViewWindowOffset) {
        val selected = selection
        if (selected == null) {
            onSelectionChanged(null, null)
        } else {
            onSelectionChanged(
                selected.text,
                Rect(
                    (textViewWindowOffset.x + selected.anchor.left).toInt(),
                    (textViewWindowOffset.y + selected.anchor.top).toInt(),
                    (textViewWindowOffset.x + selected.anchor.right).toInt(),
                    (textViewWindowOffset.y + selected.anchor.bottom).toInt(),
                ),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                containerSize = size.toSize()
            },
    ) {
        AndroidView(
            factory = { ctx ->
                SelectableTextView(ctx).apply {
                    setPadding(marginPx, marginPx, marginPx, marginPx)
                    setTextColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.WHITE)
                    textSize = fontSizeSp
                    setLineSpacing(0f, lineSpacingMultiplier)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
                        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
                    }
                    setTextIsSelectable(true)
                    this.onSelectionChanged = { start, end ->
                        post {
                            if (start < 0 || end <= start) {
                                selection = null
                                return@post
                            }
                            val selected = text.toString().substring(start, end)
                            if (selected.isBlank()) {
                                selection = null
                                return@post
                            }
                            selection = TextSelection(
                                text = selected,
                                anchor = selectionAnchor(this, start, end),
                            )
                        }
                    }
                }.also { textView = it }
            },
            update = { view ->
                val pageText = pages.getOrNull(currentPage) ?: ""
                if (view.text.toString() != pageText) {
                    view.text = pageText
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { textViewWindowOffset = it.positionInWindow() },
        )
    }
}

private data class TextSelection(
    val text: String,
    val anchor: Rect,
)

// Shared single source of truth for the page margin (3 characters wide),
// used by the TXT/EPUB reader, the PDF text paginator, and the PDF text view
// so all of them lay out within the same content box.
internal fun textMarginPx(context: Context, fontSizeSp: Float): Int {
    val measurePaint = TextPaint().apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            fontSizeSp,
            context.resources.displayMetrics,
        )
    }
    return measurePaint.measureText("000").toInt().coerceAtLeast(0)
}

private val OPENING_PUNCT = "\"'“‘([«"

// A line that continues a sentence: optionally opens with a quote/bracket,
// then a lowercase letter. Lines starting uppercase/digits/CJK look like
// verse, headings, or list items and keep their break.
private fun isContinuation(line: String): Boolean {
    var i = 0
    if (i < line.length && line[i] in OPENING_PUNCT) i++
    return i < line.length && line[i].isLowerCase()
}

internal fun normalizeForReflow(text: String): String {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n')
        .lines()
        .map { it.replace(Regex("[ \t]+"), " ").trim() }
    val out = StringBuilder()
    var prev: String? = null
    for (line in lines) {
        if (line.isEmpty()) {
            if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append("\n\n")
            prev = null
            continue
        }
        when {
            prev == null -> Unit
            prev.endsWith("-") -> out.deleteCharAt(out.length - 1)
            isContinuation(line) -> out.append(" ")
            else -> out.append("\n")
        }
        out.append(line)
        prev = line
    }
    return out.toString().trim()
}

internal fun computePages(
    context: Context,
    text: String,
    width: Int,
    height: Int,
    fontSizeSp: Float,
    lineSpacingMultiplier: Float,
    paddingPx: Int,
): List<String> {
    if (width <= 0 || height <= 0) {
        LogManager.d("TextReaderScreen", "computePages invalid size width=$width height=$height")
        return listOf(text)
    }

    val normalizedText = normalizeForReflow(text)

    val paint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            fontSizeSp,
            context.resources.displayMetrics,
        )
    }

    val availableWidth = width - paddingPx * 2

    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val builder = StaticLayout.Builder.obtain(normalizedText, 0, normalizedText.length, paint, availableWidth)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(true)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        builder.build()
    } else {
        @Suppress("DEPRECATION")
        StaticLayout(normalizedText, paint, availableWidth, Layout.Alignment.ALIGN_NORMAL, lineSpacingMultiplier, 0f, false)
    }

    // includePad=true makes the laid-out height exceed the sum of line-box
    // heights; each page relaid out in the TextView carries the same extra
    // height, so reserve room for it or the last line overflows the page.
    val lineExtent = if (layout.lineCount > 0) {
        layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(0)
    } else {
        0
    }
    val extraVerticalPad = (layout.height - lineExtent).coerceAtLeast(0)
    val availableHeight = height.toFloat() - paddingPx * 2 - extraVerticalPad

    val slices = slicePages(
        lineCount = layout.lineCount,
        lineHeight = { (layout.getLineBottom(it) - layout.getLineTop(it)).toFloat() },
        availableHeight = availableHeight,
    )
    return slices.map { slice ->
        val startOffset = layout.getLineStart(slice.first)
        val endOffset = layout.getLineEnd(slice.last)
        normalizedText.substring(startOffset, endOffset)
    }.ifEmpty {
        LogManager.d("TextReaderScreen", "computePages fallback lineCount=${layout.lineCount}")
        listOf(text)
    }
}

/**
 * Greedily groups lines into pages: lines fill the page until the next line
 * would exceed [availableHeight], then a new page starts. A single line taller
 * than the whole page still gets its own page (callers render it clipped).
 * Returned ranges are inclusive line indices.
 */
internal fun slicePages(
    lineCount: Int,
    lineHeight: (Int) -> Float,
    availableHeight: Float,
): List<IntRange> {
    val slices = mutableListOf<IntRange>()
    var startLine = 0
    var currentHeight = 0f
    for (line in 0 until lineCount) {
        val height = lineHeight(line)
        if (currentHeight + height > availableHeight && startLine < line) {
            slices.add(startLine until line)
            startLine = line
            currentHeight = 0f
        }
        currentHeight += height
    }
    if (startLine < lineCount) {
        slices.add(startLine until lineCount)
    }
    return slices
}
