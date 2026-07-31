package com.runerback.translator.reader.pdf

import android.graphics.Bitmap
import com.runerback.translator.util.LogManager
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.text.BeginText
import com.tom_roush.pdfbox.contentstream.operator.text.EndText
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject

/**
 * Extracts inline images from a single PDF page while preserving their vertical
 * position so they can be placed in reading order later.
 */
internal class PdfImageExtractor : PDFStreamEngine() {

    private val _images = mutableListOf<PdfImage>()
    val images: List<PdfImage> get() = _images

    init {
        // Register the operators needed to traverse the content stream and
        // intercept image XObjects.
        addOperator(DrawObject())
        addOperator(SetGraphicsStateParameters())
        addOperator(Save())
        addOperator(Restore())
        addOperator(Concatenate())
        addOperator(BeginText())
        addOperator(EndText())
    }

    @Deprecated("Deprecated in Java")
    override fun processOperator(operator: Operator?, operands: List<COSBase>?) {
        if (operator != null && operator.name == "Do" && !operands.isNullOrEmpty()) {
            val name = operands.firstOrNull { it is COSName } as? COSName
            if (name != null) {
                extractImage(name)
            }
        }
        super.processOperator(operator, operands)
    }

    private fun extractImage(name: COSName) {
        val resources = resources ?: return
        val xObject = resources.getXObject(name)
        if (xObject is PDImageXObject) {
            val bitmap = runCatching { xObject.image }.getOrElse { e ->
                LogManager.e("PdfImageExtractor", "failed to decode image", e)
                null
            } ?: return

            val ctm = graphicsState.currentTransformationMatrix
            val y = ctm.translateY
            _images.add(PdfImage(bitmap = bitmap, sortY = y))
        }
    }
}
