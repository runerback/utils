package com.runerback.translator.reader.text

import android.graphics.Rect
import android.text.Selection
import android.widget.TextView
import java.text.BreakIterator

/**
 * Selects the word under the window-space point. This function owns the
 * global→local conversion and the view's padding — callers pass raw window
 * coordinates and know nothing about the layout. Returns false when the
 * point does not land on text.
 */
internal fun selectWordAt(textView: TextView, windowX: Float, windowY: Float): Boolean {
    val location = IntArray(2)
    textView.getLocationInWindow(location)
    val charOffset = offsetForTouch(textView, windowX - location[0], windowY - location[1])
    if (charOffset < 0) return false
    val text = textView.text?.toString() ?: return false
    val (start, end) = findWordBounds(text, charOffset)
    if (start >= end) return false
    val spannable = textView.text as? android.text.Spannable ?: return false
    Selection.setSelection(spannable, start, end)
    textView.requestFocus()
    return true
}

// Same conversion TextView.getOffsetForPosition performs internally; done
// explicitly because some firmwares skip the padding step in that API.
internal fun offsetForTouch(textView: TextView, viewX: Float, viewY: Float): Int {
    val layout = textView.layout ?: return -1
    val layoutX = viewX - textView.totalPaddingLeft + textView.scrollX
    val layoutY = viewY - textView.totalPaddingTop + textView.scrollY
    if (layoutY < 0 || layoutY >= layout.height) return -1
    val line = layout.getLineForVertical(layoutY.toInt())
    return layout.getOffsetForHorizontal(line, layoutX)
}

/** Window-space rect of the selected glyphs; includes the view's padding so
 *  composing it with the view's window offset lands exactly on the text. */
internal fun selectionAnchor(textView: TextView, start: Int, end: Int): Rect {
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

internal fun findWordBounds(text: String, offset: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0
    val iterator = BreakIterator.getWordInstance()
    iterator.setText(text)
    val boundedOffset = offset.coerceIn(0, text.length)
    var start = iterator.preceding(boundedOffset)
    if (start == BreakIterator.DONE) start = 0
    var end = iterator.following(boundedOffset)
    if (end == BreakIterator.DONE) end = text.length
    while (end > start && text[end - 1].isWhitespace()) {
        end--
    }
    while (start < end && text[start].isWhitespace()) {
        start++
    }
    return start to end
}
