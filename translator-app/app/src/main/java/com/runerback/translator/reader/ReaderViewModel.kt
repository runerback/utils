package com.runerback.translator.reader

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel

/** Which edge panel is open; [NONE] is the all-clear reader state. */
enum class SettingsPanel { NONE, TOP, BOTTOM }

/** A one-shot event tagged with a monotonically increasing [id] so consumers
 *  can consume each emission exactly once. */
data class IndexedOffset(val id: Int, val offset: Offset)

/** A confirmed text selection: the selected [text], the glyph rect in window
 *  coordinates where the translate toolbar is anchored, and the window
 *  coordinate of the long-press that started the selection, which anchors
 *  the translation dialog. */
data class ReaderSelection(
    val text: String,
    val toolbarAnchor: Rect,
    val longPressStart: Offset,
)

/**
 * Owns every reader interaction rule. The [ReaderInputLayer] pushes raw tap
 * (as a [ReaderZone]) and long-press (as a raw window [Offset]) events in
 * here; this class decides what they mean. It knows nothing about padding,
 * view positions, or coordinates beyond the long-press point — renderers
 * convert coordinates internally.
 */
class ReaderViewModel : ViewModel() {

    /** Pushed by the reader UI. */
    var hasBitmap by mutableStateOf(false)
    var pageIndex by mutableIntStateOf(0)
    var totalPages by mutableIntStateOf(1)

    /** Owned interaction state. */
    var settings by mutableStateOf(SettingsPanel.NONE)
        private set
    var selection by mutableStateOf<ReaderSelection?>(null)
        private set
    var cropRequest by mutableStateOf<IndexedOffset?>(null)
        private set

    /** One-shot events consumed by the reader UI. */
    var selectWordRequest by mutableStateOf<IndexedOffset?>(null)
        private set
    var clearSelectionSignal by mutableIntStateOf(0)
        private set
    var navEvent by mutableStateOf<NavEvent?>(null)
        private set

    val menusVisible: Boolean
        get() = settings != SettingsPanel.NONE
    val ocrSelectorVisible: Boolean
        get() = cropRequest != null

    data class NavEvent(val id: Int, val page: Int)

    private var requestId = 0
    private var navId = 0
    private var pendingLongPressStart = Offset.Zero

    fun onPageInfo(page: Int, total: Int) {
        pageIndex = page
        totalPages = total
    }

    fun onClick(zone: ReaderZone) {
        val selected = selection
        if (selected != null) {
            clearSelection()
            return
        }
        if (ocrSelectorVisible) return
        if (settings != SettingsPanel.NONE) {
            settings = SettingsPanel.NONE
            return
        }
        when (zone) {
            ReaderZone.LEFT -> navigate(pageIndex - 1)
            ReaderZone.RIGHT -> navigate(pageIndex + 1)
            ReaderZone.TOP -> openSettings(SettingsPanel.TOP)
            ReaderZone.BOTTOM -> openSettings(SettingsPanel.BOTTOM)
            ReaderZone.CENTER -> Unit
        }
    }

    fun onLongPress(global: Offset) {
        if (selection != null || ocrSelectorVisible || settings != SettingsPanel.NONE) return
        pendingLongPressStart = global
        if (hasBitmap) {
            cropRequest = IndexedOffset(++requestId, global)
        } else {
            selectWordRequest = IndexedOffset(++requestId, global)
        }
    }

    /** Reported by the text renderer after the selection span actually changed. */
    fun onTextSelected(text: String?, toolbarAnchor: Rect?) {
        selection = if (text.isNullOrEmpty() || toolbarAnchor == null) {
            null
        } else {
            ReaderSelection(text, toolbarAnchor, pendingLongPressStart)
        }
    }

    fun onSelectWordHandled(id: Int) {
        if (selectWordRequest?.id == id) selectWordRequest = null
    }

    fun onCropSelectorClosed() {
        cropRequest = null
    }

    fun clearSelection() {
        selection = null
        clearSelectionSignal++
    }

    fun consumeNavEvent() {
        navEvent = null
    }

    private fun openSettings(panel: SettingsPanel) {
        cropRequest = null
        settings = panel
    }

    private fun navigate(page: Int) {
        val target = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        if (target != pageIndex) navEvent = NavEvent(++navId, target)
    }
}
