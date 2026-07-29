package com.runerback.translator.reader.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfRendererWrapper(context: Context, uri: Uri) {

    private val fileDescriptor: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
    private val renderer: PdfRenderer? = fileDescriptor?.let { PdfRenderer(it) }

    val pageCount: Int
        get() = renderer?.pageCount ?: 0

    suspend fun renderPage(index: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        renderer?.let { pdf ->
            pdf.openPage(index).use { page ->
                val height = (width.toFloat() / page.width * page.height).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    fun close() {
        renderer?.close()
        fileDescriptor?.close()
    }
}
