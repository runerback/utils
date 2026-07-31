package com.runerback.translator.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.runerback.translator.util.LogManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts text and images from a PDF using the free Apache PDFBox Android port.
 *
 * The extractor is stateless; the caller is responsible for closing the source Uri.
 */
class PdfContentExtractor(private val context: Context) {

    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    /**
     * Extracts the requested page. Returns null if the document cannot be opened
     * or the page index is out of range.
     */
    suspend fun extractPage(uri: Uri, pageIndex: Int): PdfPageContent? =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureInitialized()
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { document ->
                        if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                            LogManager.d(
                                "PdfContentExtractor",
                                "page index out of range: $pageIndex / ${document.numberOfPages}",
                            )
                            return@use null
                        }
                        val page = document.getPage(pageIndex)
                        val text = extractPageText(document, pageIndex)
                        val images = extractPageImages(page)
                        PdfPageContent(text = text, images = images)
                    }
                }
            }.getOrElse { e ->
                LogManager.e("PdfContentExtractor", "failed to extract page $pageIndex uri=$uri", e)
                null
            }
        }

    /**
     * Returns the number of pages in the PDF, or null if the document cannot be opened.
     */
    suspend fun pageCount(uri: Uri): Int? = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { it.numberOfPages }
            }
        }.getOrElse { e ->
            LogManager.e("PdfContentExtractor", "failed to get page count uri=$uri", e)
            null
        }
    }

    private fun extractPageText(document: PDDocument, pageIndex: Int): String {
        val stripper = PDFTextStripper().apply {
            startPage = pageIndex + 1 // PDFBox pages are 1-based.
            endPage = pageIndex + 1
            sortByPosition = true
        }
        return stripper.getText(document).trim()
    }

    private fun extractPageImages(page: com.tom_roush.pdfbox.pdmodel.PDPage): List<PdfImage> {
        return runCatching {
            val extractor = PdfImageExtractor()
            extractor.processPage(page)
            extractor.images.sortedBy { it.sortY }
        }.getOrElse { e ->
            LogManager.e("PdfContentExtractor", "failed to extract images", e)
            emptyList()
        }
    }
}
