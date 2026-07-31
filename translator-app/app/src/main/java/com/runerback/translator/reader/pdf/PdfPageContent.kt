package com.runerback.translator.reader.pdf

import android.graphics.Bitmap

/**
 * Extracted content of a single PDF page, ordered for reading.
 *
 * @param text The page's extractable text. Empty when the page is image-only.
 * @param images Images found on the page, sorted top-to-bottom.
 */
data class PdfPageContent(
    val text: String,
    val images: List<PdfImage> = emptyList(),
) {
    val hasContent: Boolean
        get() = text.isNotBlank() || images.isNotEmpty()
}

/**
 * @param bitmap The decoded image.
 * @param sortY Vertical position in PDF page coordinates; used to place the
 *              image in reading order relative to text blocks.
 */
data class PdfImage(
    val bitmap: Bitmap,
    val sortY: Float,
)
