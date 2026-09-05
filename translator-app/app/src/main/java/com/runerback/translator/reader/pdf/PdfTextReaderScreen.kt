package com.runerback.translator.reader.pdf

import android.graphics.Rect
import android.os.Build
import android.text.Layout
import android.text.Selection
import android.text.Spannable
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
import com.runerback.translator.reader.IndexedOffset
import com.runerback.translator.reader.text.SelectableTextView
import com.runerback.translator.reader.text.selectWordAt
import com.runerback.translator.reader.text.selectionAnchor
import com.runerback.translator.reader.text.textMarginPx
import com.runerback.translator.util.LogManager

private data class TextSelection(
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
    selectWordRequest: IndexedOffset? = null,
    clearSelectionSignal: Int = 0,
    onSelectWordHandled: (Int) -> Unit = {},
    onPageChange: (Int, Int) -> Unit = { _, _ -> },
    onTotalPages: (Int) -> Unit = {},
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
            selectWordRequest = selectWordRequest,
            clearSelectionSignal = clearSelectionSignal,
            onSelectWordHandled = onSelectWordHandled,
            debug = debug,
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
    selectWordRequest: IndexedOffset?,
    clearSelectionSignal: Int,
    onSelectWordHandled: (Int) -> Unit,
    debug: Boolean,
    onSelectionUpdate: (String?, anchor: Rect?) -> Unit,
) {
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var textView by remember { mutableStateOf<TextView?>(null) }
    var textViewWindowOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val context = LocalContext.current
    // Same margin as the paginator uses; carried as the TextView's internal
    // padding so every coordinate conversion happens inside this screen.
    val marginPx = remember(density, fontSizeSp) { textMarginPx(context, fontSizeSp) }

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
                        setPadding(marginPx, marginPx, marginPx, marginPx)
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
                                if (start < 0 || end <= start) {
                                    selection = null
                                    return@post
                                }
                                val selected = this.text.toString().substring(start, end)
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
                    if (view.text.toString() != text) {
                        if (debug) {
                            LogManager.d("PdfTextReaderScreen", "setting text length=${text.length}")
                        }
                        view.text = text
                    }
                },
                modifier = Modifier
                    .wrapContentHeight()
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
