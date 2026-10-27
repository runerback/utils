package com.runerback.imagehelper.model

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import java.util.UUID

/**
 * Transform of a frame image relative to the GIF canvas.
 * scale is multiplicative on top of the base "fit-center" scale,
 * offset is in canvas pixels from the canvas center.
 */
data class FrameTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero
)

data class GifFrame(
    val id: String = UUID.randomUUID().toString(),
    val imageUri: Uri,
    val transform: FrameTransform = FrameTransform(),
    val durationMs: Int = 100
)
