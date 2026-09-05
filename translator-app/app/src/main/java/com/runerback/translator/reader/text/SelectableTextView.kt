package com.runerback.translator.reader.text

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.MotionEvent
import android.widget.TextView
import com.runerback.translator.R

class SelectableTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs) {

    var onSelectionChanged: ((Int, Int) -> Unit)? = null

    init {
        setTextSelectHandle(R.drawable.transparent_handle)
        setTextSelectHandleLeft(R.drawable.transparent_handle)
        setTextSelectHandleRight(R.drawable.transparent_handle)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }

    // Selection gestures are owned by the reader's input layer: it forwards
    // long-presses to the renderer, which sets the selection span
    // programmatically (see selectWordAt). Native touch-driven selection must
    // stay off or the two paths fight; the highlight still renders because
    // the span is set explicitly and focus is requested there.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = null
}
