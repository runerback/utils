package com.runerback.imagehelper.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.runerback.imagehelper.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "BitmapLoader"

/**
 * Decodes the image at [uri] downsampling so both dimensions stay near
 * [maxDimension] pixels. Returns null while decoding or on failure.
 */
@Composable
fun rememberDecodedBitmap(uri: Uri?, maxDimension: Int): ImageBitmap? {
    val context = LocalContext.current
    val bitmap = remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, maxDimension) {
        bitmap.value = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeSampledBitmap(context, uri, maxDimension)
            }
        }
    }
    return bitmap.value
}

private fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): ImageBitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            LogManager.w(TAG, "decodeSampledBitmap: could not read bounds of $uri")
            return null
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension &&
            bounds.outHeight / (sample * 2) >= maxDimension
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }?.asImageBitmap()
    } catch (e: Exception) {
        LogManager.e(TAG, "decodeSampledBitmap failed for $uri", e)
        null
    }
}
