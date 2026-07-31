package com.runerback.translator.reader.text

import android.graphics.Rect
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.widget.TextView
import com.runerback.translator.util.LogManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun TextReaderScreen(
    content: String,
    initialPage: Int = 0,
    fontSizeSp: Float = 18f,
    lineSpacingMultiplier: Float = 1.3f,
    menusVisible: Boolean = false,
    onPageChange: (Int, Int) -> Unit = { _, _ -> },
    onTotalPages: (Int) -> Unit = {},
    onTranslate: (String, anchor: Rect) -> Unit,
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var pages by remember { mutableStateOf(listOf("")) }
    var currentPage by remember(initialPage) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    val paddingPx = remember(density, containerSize.width) {
        if (containerSize.width <= 0f) return@remember 0
        val maxPaddingPx = with(density) { 8.dp.toPx() }
        val calculatedPaddingPx = containerSize.width * 0.015f
        minOf(calculatedPaddingPx, maxPaddingPx).toInt().coerceAtLeast(0)
    }
    var selection by remember { mutableStateOf<Selection?>(null) }

    LaunchedEffect(content, containerSize, paddingPx) {
        if (content.isEmpty()) {
            LogManager.d("TextReaderScreen", "empty content")
            return@LaunchedEffect
        }
        if (containerSize.width <= 0 || containerSize.height <= 0) {
            LogManager.d("TextReaderScreen", "waiting for layout size=$containerSize")
            return@LaunchedEffect
        }
        val computed = computePages(
            text = content,
            width = containerSize.width.toInt(),
            height = containerSize.height.toInt(),
            fontSizeSp = fontSizeSp,
            lineSpacingMultiplier = lineSpacingMultiplier,
            paddingPx = paddingPx,
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

    LaunchedEffect(currentPage, menusVisible) {
        selection = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp)
            .onSizeChanged { size ->
                containerSize = size.toSize()
            },
    ) {
        AndroidView(
            factory = { ctx ->
                val view = SelectableTextView(ctx).apply {
                    setTextColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.WHITE)
                    textSize = fontSizeSp
                    setLineSpacing(0f, lineSpacingMultiplier)
                    setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                    setTextIsSelectable(true)
                }
                view.onSelectionChanged = { start, end ->
                    view.post {
                        val menusVisibleNow = view.tag as? Boolean ?: false
                        if (menusVisibleNow || start < 0 || end <= start) {
                            selection = null
                            return@post
                        }
                        val text = view.text.toString()
                        val selected = text.substring(start, end)
                        if (selected.isBlank()) {
                            selection = null
                            return@post
                        }
                        selection = Selection(
                            text = selected,
                            anchor = getSelectionAnchor(view, start, end),
                        )
                    }
                }
                view
            },
            update = { textView ->
                textView.tag = menusVisible
                if (menusVisible) {
                    textView.setTextIsSelectable(false)
                    selection = null
                } else {
                    textView.setTextIsSelectable(true)
                }
                val pageText = pages.getOrNull(currentPage) ?: ""
                if (textView.text.toString() != pageText) {
                    textView.text = pageText
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        selection?.let { (selected, anchor) ->
            SelectionToolbar(
                anchor = anchor,
                onTranslate = {
                    onTranslate(selected, anchor)
                    selection = null
                },
            )
        }
    }
}

private data class Selection(
    val text: String,
    val anchor: Rect,
)

@Composable
private fun SelectionToolbar(
    anchor: Rect,
    onTranslate: () -> Unit,
) {
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val toolbarWidth = toolbarSize.width
    val toolbarHeight = toolbarSize.height
    val centerX = (anchor.left + anchor.right) / 2
    val x = centerX - toolbarWidth / 2
    val y = if (anchor.top - toolbarHeight >= 0) {
        anchor.top - toolbarHeight
    } else {
        anchor.bottom
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(x, y) }
            .onGloballyPositioned { toolbarSize = it.size }
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable(onClick = onTranslate)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Translate",
            color = Color.Black,
            fontSize = 14.sp,
        )
    }
}

private fun computePages(
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

    val paint = TextPaint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            fontSizeSp,
            android.content.res.Resources.getSystem().displayMetrics,
        )
    }

    val availableWidth = width - paddingPx * 2
    val availableHeight = height - paddingPx * 2

    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        StaticLayout.Builder.obtain(text, 0, text.length, paint, availableWidth)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .build()
    } else {
        @Suppress("DEPRECATION")
        StaticLayout(text, paint, availableWidth, Layout.Alignment.ALIGN_NORMAL, lineSpacingMultiplier, 0f, false)
    }

    val pages = mutableListOf<String>()
    val lineCount = layout.lineCount
    var startLine = 0
    var currentHeight = 0f

    for (line in 0 until lineCount) {
        val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
        if (currentHeight + lineHeight > availableHeight && startLine < line) {
            val startOffset = layout.getLineStart(startLine)
            val endOffset = layout.getLineEnd(line - 1)
            pages.add(text.substring(startOffset, endOffset))
            startLine = line
            currentHeight = 0f
        }
        currentHeight += lineHeight
    }

    if (startLine < lineCount) {
        val startOffset = layout.getLineStart(startLine)
        val endOffset = layout.getLineEnd(lineCount - 1)
        pages.add(text.substring(startOffset, endOffset))
    }

    return pages.ifEmpty {
        LogManager.d("TextReaderScreen", "computePages fallback lineCount=$lineCount")
        listOf(text)
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

    return Rect(left, top, right, bottom)
}
