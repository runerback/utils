package com.runerback.tagem.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AnimatedGifImage(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentScale: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER,
) {
    val context = LocalContext.current
    val imageView = remember {
        ImageView(context).apply {
            this.scaleType = contentScale
        }
    }

    DisposableEffect(uri) {
        val drawable = try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable
        } catch (_: Exception) {
            null
        }
        imageView.setImageDrawable(drawable)
        drawable?.start()

        onDispose {
            drawable?.stop()
            imageView.setImageDrawable(null)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { imageView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
