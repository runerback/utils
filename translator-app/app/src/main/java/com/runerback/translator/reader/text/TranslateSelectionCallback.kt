package com.runerback.translator.reader.text

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView

class TranslateSelectionCallback(
    private val textView: TextView,
    private val onTranslate: (String) -> Unit,
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.clear()
        menu?.add(0, MENU_ITEM_TRANSLATE, 0, "Translate")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        if (item?.itemId == MENU_ITEM_TRANSLATE) {
            val start = textView.selectionStart
            val end = textView.selectionEnd
            if (start >= 0 && end > start) {
                val selected = textView.text.substring(start, end)
                onTranslate(selected)
            }
            mode?.finish()
            return true
        }
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode?) {}

    companion object {
        private const val MENU_ITEM_TRANSLATE = 1001
    }
}
