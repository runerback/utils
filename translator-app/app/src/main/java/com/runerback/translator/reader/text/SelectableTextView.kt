package com.runerback.translator.reader.text

import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
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

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = null
}
