package com.runerback.translator.reader.pdf

import android.graphics.Rect
import android.os.Build
import android.text.Layout
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.runerback.translator.reader.text.SelectableTextView
import com.runerback.translator.reader.text.textMarginPx
import com.runerback.translator.util.LogManager

private data class Selection(
    val text: String,
    val anchor: Rect,
)

/**
 * Renders one screen-sized text chunk from a PDF page.
 *
 * Any images embedded in the page are appended below the text chunk in the
 * same scrollable column.
 */
@Composable
fun PdfTextReaderScreen(
    text: String,
    images: List<PdfImage>,
    pageIndex: Int,
    totalPages: Int,
    fontSizeSp: Float = 18f,
    lineHeight: Float = 1.3f,
    menusVisible: Boolean = false,
    onTranslate: (String, anchor: Rect) -> Unit,
    onPageChange: (Int, Int) -> Unit = { _, _ -> },
    onTotalPages: (Int) -> Unit = {},
    onTextViewReady: (TextView?) -> Unit = {},
    onSelectionChanged: (String?, anchor: Rect?) -> Unit = { _, _ -> },
    debug: Boolean = false,
) {
    LaunchedEffect(pageIndex, totalPages) {
        val total = totalPages.coerceAtLeast(1)
        val page = pageIndex.coerceIn(0, total - 1)
        if (debug) {
            LogManager.d(
                "PdfTextReaderScreen",
                "page=$page total=$total textLength=${text.length} images=${images.size}",
            )
        }
        onTotalPages(total)
        onPageChange(page, total)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        TextPageContent(
            text = text,
            images = images,
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            menusVisible = menusVisible,
            debug = debug,
            onTranslate = onTranslate,
            onTextViewReady = onTextViewReady,
            onSelectionUpdate = onSelectionChanged,
        )
    }
}

@Composable
private fun TextPageContent(
    text: String,
    images: List<PdfImage>,
    fontSizeSp: Float,
    lineHeight: Float,
    menusVisible: Boolean,
    debug: Boolean,
    onTranslate: (String, anchor: Rect) -> Unit,
    onTextViewReady: (TextView?) -> Unit = {},
    onSelectionUpdate: (String?, anchor: Rect?) -> Unit = { _, _ -> },
) {
    var selection by remember { mutableStateOf<Selection?>(null) }
    var textViewWindowOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val context = LocalContext.current
    // Same margin as the paginator uses; applied around the TextView (which
    // keeps zero padding) so layout coordinates equal view coordinates.
    val marginPx = remember(density, fontSizeSp) { textMarginPx(context, fontSizeSp) }
    val marginDp = with(density) { marginPx.toDp() }

    DisposableEffect(Unit) {
        onDispose { onTextViewReady(null) }
    }

    LaunchedEffect(selection, textViewWindowOffset) {
        val selected = selection
        if (selected == null) {
            onSelectionUpdate(null, null)
        } else {
            onSelectionUpdate(
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
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AndroidView(
                factory = { ctx ->
                    SelectableTextView(ctx).apply {
                        setTextColor(android.graphics.Color.BLACK)
                        setBackgroundColor(android.graphics.Color.WHITE)
                        textSize = fontSizeSp
                        setLineSpacing(0f, lineHeight)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
                            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
                        }
                        setTextIsSelectable(true)
                        onSelectionChanged = { start, end ->
                            post {
                                val menusVisibleNow = tag as? Boolean ?: false
                                if (menusVisibleNow) {
                                    if (debug) {
                                        LogManager.d("PdfTextReaderScreen", "selection ignored: menusVisible=true")
                                    }
                                    selection = null
                                    return@post
                                }
                                if (start < 0 || end <= start) {
                                    if (debug) {
                                        LogManager.d("PdfTextReaderScreen", "selection cleared: start=$start end=$end")
                                    }
                                    selection = null
                                    return@post
                                }
                                val selected = this.text.toString().substring(start, end)
                                if (selected.isBlank()) {
                                    if (debug) {
                                        LogManager.d("PdfTextReaderScreen", "selection rejected: blank start=$start end=$end")
                                    }
                                    selection = null
                                    return@post
                                }
                                val relativeAnchor = getSelectionAnchor(this, start, end)
                                if (debug) {
                                    LogManager.d(
                                        "PdfTextReaderScreen",
                                        "selection accepted: start=$start end=$end len=${selected.length} anchor=$relativeAnchor",
                                    )
                                }
                                selection = Selection(text = selected, anchor = relativeAnchor)
                            }
                        }
                        onTextViewReady(this)
                    }
                },
                update = { textView ->
                    textView.tag = menusVisible
                    if (menusVisible) {
                        textView.setTextIsSelectable(false)
                        selection = null
                    } else {
                        textView.setTextIsSelectable(true)
                    }
                    if (textView.text.toString() != text) {
                        if (debug) {
                            LogManager.d("PdfTextReaderScreen", "setting text length=${text.length}")
                        }
                        textView.text = text
                    }
                },
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(marginDp)
                    .onGloballyPositioned { textViewWindowOffset = it.positionInWindow() },
            )

            images.forEach { image ->
                Image(
                    bitmap = image.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .then(if (debug) Modifier.border(2.dp, Color.Red) else Modifier),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun getSelectionAnchor(textView: TextView, start: Int, end: Int): Rect {
    val layout = textView.layout ?: return Rect()
    val lineStart = layout.getLineForOffset(start)
    val lineEnd = layout.getLineForOffset(end)

    var left = Int.MAX_VALUE
    var top = Int.MAX_VALUE
    var right = Int.MIN_VALUE
    var bottom = Int.MIN_VALUE

    for (line in lineStart..lineEnd) {
        val lineLeft = layout.getPrimaryHorizontal(start.coerceAtLeast(layout.getLineStart(line))).toInt()
        val lineRight = layout.getPrimaryHorizontal(end.coerceAtMost(layout.getLineEnd(line))).toInt()
        left = minOf(left, lineLeft)
        right = maxOf(right, lineRight)
        top = minOf(top, layout.getLineTop(line))
        bottom = maxOf(bottom, layout.getLineBottom(line))
    }

    val padLeft = textView.totalPaddingLeft
    val padTop = textView.totalPaddingTop

    return Rect(
        left + padLeft,
        top + padTop,
        right + padLeft,
        bottom + padTop,
    )
}
