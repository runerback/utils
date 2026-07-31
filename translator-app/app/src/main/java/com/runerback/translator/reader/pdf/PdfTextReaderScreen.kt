package com.runerback.translator.reader.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders a single PDF page's extracted content as reflowable text plus images.
 *
 * Text blocks reflow to the screen width; images are scaled to fit the width
 * while preserving aspect ratio. Content is ordered by reading order
 * (top-to-bottom).
 */
@Composable
fun PdfTextReaderScreen(
    content: PdfPageContent,
    fontSizeSp: Float = 18f,
    lineHeight: Float = 1.3f,
    debug: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        if (content.text.isNotBlank()) {
            Text(
                text = content.text,
                style = TextStyle(
                    color = Color.Black,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * lineHeight).sp,
                ),
            )
        }

        for (image in content.images) {
            Image(
                bitmap = image.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .then(if (debug) Modifier.border(2.dp, Color.Red) else Modifier),
            )
        }
    }
}
