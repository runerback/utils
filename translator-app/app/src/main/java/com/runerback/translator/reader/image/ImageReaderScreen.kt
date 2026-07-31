package com.runerback.translator.reader.image

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
fun ImageReaderScreen(
    bitmap: Bitmap,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    )
}
