package com.runerback.translator.reader

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderViewModelTest {

    private fun newViewModel(page: Int = 0, total: Int = 10, hasBitmap: Boolean = false): ReaderViewModel {
        return ReaderViewModel().apply {
            onPageInfo(page, total)
            this.hasBitmap = hasBitmap
        }
    }

    // Rule 1: all-clear click navigates or opens settings by zone.

    @Test
    fun `click in left zone navigates to previous page`() {
        val vm = newViewModel(page = 3)
        vm.onClick(ReaderZone.LEFT)
        assertEquals(2, vm.navEvent?.page)
    }

    @Test
    fun `click in right zone navigates to next page`() {
        val vm = newViewModel(page = 3)
        vm.onClick(ReaderZone.RIGHT)
        assertEquals(4, vm.navEvent?.page)
    }

    @Test
    fun `navigation is clamped at both ends`() {
        val vm = newViewModel(page = 0)
        vm.onClick(ReaderZone.LEFT)
        assertNull(vm.navEvent)

        vm.onPageInfo(9, 10)
        vm.onClick(ReaderZone.RIGHT)
        assertNull(vm.navEvent)
    }

    @Test
    fun `click in top and bottom zones opens the matching settings panel`() {
        val vm = newViewModel()
        vm.onClick(ReaderZone.TOP)
        assertEquals(SettingsPanel.TOP, vm.settings)

        vm.onClick(ReaderZone.CENTER) // closes (rule 5), then reopen bottom
        vm.onClick(ReaderZone.BOTTOM)
        assertEquals(SettingsPanel.BOTTOM, vm.settings)
    }

    @Test
    fun `click in center zone does nothing`() {
        val vm = newViewModel()
        vm.onClick(ReaderZone.CENTER)
        assertNull(vm.navEvent)
        assertEquals(SettingsPanel.NONE, vm.settings)
    }

    // Rule 2: all-clear long-press starts text or OCR selection.

    @Test
    fun `long press on text page emits a word-selection request`() {
        val vm = newViewModel(hasBitmap = false)
        vm.onLongPress(Offset(10f, 20f))
        assertEquals(Offset(10f, 20f), vm.selectWordRequest?.offset)
        assertNull(vm.cropRequest)
    }

    @Test
    fun `long press on image page emits an ocr crop request`() {
        val vm = newViewModel(hasBitmap = true)
        vm.onLongPress(Offset(30f, 40f))
        assertEquals(Offset(30f, 40f), vm.cropRequest?.offset)
        assertNull(vm.selectWordRequest)
    }

    @Test
    fun `long press is ignored while settings are open`() {
        val vm = newViewModel()
        vm.onClick(ReaderZone.TOP)
        vm.onLongPress(Offset(1f, 1f))
        assertNull(vm.selectWordRequest)
        assertNull(vm.cropRequest)
    }

    // Rule 3: while text is selected a click only clears the selection.

    @Test
    fun `click with selected text clears the selection and nothing else`() {
        val vm = newViewModel(page = 3)
        vm.onLongPress(Offset(5f, 6f))
        vm.onTextSelected("word", Rect(0, 0, 10, 10))
        assertTrue(vm.clearSelectionSignal == 0)

        vm.onClick(ReaderZone.RIGHT)
        assertNull(vm.selection)
        assertEquals(1, vm.clearSelectionSignal)
        assertNull(vm.navEvent)
        assertEquals(SettingsPanel.NONE, vm.settings)
    }

    @Test
    fun `selection records the long-press start for the dialog anchor`() {
        val vm = newViewModel()
        vm.onLongPress(Offset(7f, 8f))
        vm.onTextSelected("word", Rect(0, 0, 10, 10))
        assertEquals(Offset(7f, 8f), vm.selection?.longPressStart)
    }

    // Rule 4: while the OCR selector is shown the collector events are ignored.

    @Test
    fun `click and long press are ignored while the ocr selector is visible`() {
        val vm = newViewModel(hasBitmap = true, page = 3)
        vm.onLongPress(Offset(1f, 1f))
        assertTrue(vm.ocrSelectorVisible)

        vm.onClick(ReaderZone.RIGHT)
        assertNull(vm.navEvent)
        assertEquals(SettingsPanel.NONE, vm.settings)

        vm.onLongPress(Offset(2f, 2f))
        assertEquals(1, vm.cropRequest?.id)
    }

    // Rule 5: while settings are shown a click only closes them.

    @Test
    fun `click while settings are open closes them without navigating`() {
        val vm = newViewModel(page = 3)
        vm.onClick(ReaderZone.TOP)
        vm.onClick(ReaderZone.RIGHT)
        assertEquals(SettingsPanel.NONE, vm.settings)
        assertNull(vm.navEvent)
    }

    @Test
    fun `opening settings closes the ocr selector`() {
        val vm = newViewModel(hasBitmap = true)
        vm.onLongPress(Offset(1f, 1f))
        vm.onCropSelectorClosed()
        vm.onClick(ReaderZone.TOP)
        assertNull(vm.cropRequest)
    }

    @Test
    fun `select word request is consumed once`() {
        val vm = newViewModel()
        vm.onLongPress(Offset(1f, 1f))
        val request = vm.selectWordRequest
        assertTrue(request != null)
        vm.onSelectWordHandled(request!!.id)
        assertNull(vm.selectWordRequest)
    }
}
