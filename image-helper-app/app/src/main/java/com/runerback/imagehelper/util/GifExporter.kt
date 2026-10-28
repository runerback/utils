package com.runerback.imagehelper.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.runerback.imagehelper.model.GifFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Renders the frames onto the GIF canvas and encodes an animated GIF
 * into the stream behind [output]. [fitScale] is the scale the workzone
 * displays the canvas at, used to convert screen-pixel frame offsets
 * into GIF pixels. [background] is the ARGB color shown behind the frames.
 */
object GifExporter {

    private const val TAG = "GifExporter"

    /** GIF frame delay is a 16-bit field in hundredths of a second */
    private const val MAX_DELAY_MS = 655_350

    suspend fun export(
        context: Context,
        output: Uri,
        frames: List<GifFrame>,
        width: Int,
        height: Int,
        background: Int,
        fitScale: Float,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(frames.isNotEmpty()) { "no frames to export" }
            require(fitScale > 0f) { "invalid canvas scale" }
            LogManager.i(TAG, "export start: ${frames.size} frames, ${width}x$height, fitScale=$fitScale -> $output")
            val stream = context.contentResolver.openOutputStream(output, "wt")
                ?: error("could not open output destination")
            stream.use {
                val encoder = GifEncoder(width, height)
                if (!encoder.start(it)) error("could not start GIF stream")
                encoder.setRepeat(0)

                // pending frame: written only when the next frame differs, so
                // runs of identical frames collapse into one frame with the
                // summed duration; differing frames get a transparent diff mask
                var pendingBitmap: Bitmap? = null
                var pendingMask: BooleanArray? = null
                var pendingDelayMs = 0
                var previousPixels: IntArray? = null

                fun flushPending() {
                    val bitmap = pendingBitmap ?: return
                    encoder.setDelay(pendingDelayMs)
                    if (!encoder.addFrame(bitmap, pendingMask)) error("could not encode frame")
                    bitmap.recycle()
                    pendingBitmap = null
                }

                frames.forEachIndexed { index, frame ->
                    val source = decodeBitmap(context, frame.imageUri, width, height)
                        ?: error("could not decode frame ${index + 1}")
                    val rendered = renderFrame(source, frame, width, height, background, fitScale)
                    source.recycle()

                    val pixels = IntArray(width * height)
                    rendered.getPixels(pixels, 0, width, 0, 0, width, height)

                    if (previousPixels != null && pixels.contentEquals(previousPixels)) {
                        pendingDelayMs = (pendingDelayMs + frame.durationMs).coerceAtMost(MAX_DELAY_MS)
                        rendered.recycle()
                    } else {
                        flushPending()
                        val prev = previousPixels
                        pendingMask = prev?.let { p ->
                            BooleanArray(width * height) { i -> pixels[i] == p[i] }
                        }
                        pendingBitmap = rendered
                        pendingDelayMs = frame.durationMs
                        previousPixels = pixels
                    }
                    onProgress((index + 1).toFloat() / frames.size)
                }
                flushPending()

                if (!encoder.finish()) error("could not finalize GIF")
            }
        }.onFailure { LogManager.e(TAG, "export failed", it) }
            .onSuccess { LogManager.i(TAG, "exported ${frames.size} frames to $output") }
    }

    private fun decodeBitmap(context: Context, uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = context.contentResolver.openInputStream(uri)
            if (boundsStream == null) {
                LogManager.w(TAG, "decodeBitmap: cannot open stream for bounds: $uri")
                return null
            }
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                LogManager.w(TAG, "decodeBitmap: unreadable bounds ${bounds.outWidth}x${bounds.outHeight}: $uri")
                return null
            }
            // frames may be zoomed in (transform.scale up to 20), decode generously
            val target = maxOf(width, height) * 4
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target &&
                bounds.outHeight / (sample * 2) >= target
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (bitmap == null) {
                LogManager.w(TAG, "decodeBitmap: decodeStream returned null (${bounds.outWidth}x${bounds.outHeight}, sample=$sample): $uri")
            }
            bitmap
        } catch (e: Exception) {
            LogManager.e(TAG, "decodeBitmap failed: $uri", e)
            null
        }
    }

    private fun renderFrame(
        source: Bitmap,
        frame: GifFrame,
        width: Int,
        height: Int,
        background: Int,
        fitScale: Float
    ): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(background)
        val baseScale = min(width.toFloat() / source.width, height.toFloat() / source.height)
        val scale = baseScale * frame.transform.scale
        val w = source.width * scale
        val h = source.height * scale
        val left = (width - w) / 2f + frame.transform.offset.x / fitScale
        val top = (height - h) / 2f + frame.transform.offset.y / fitScale
        canvas.drawBitmap(
            source,
            null,
            RectF(left, top, left + w, top + h),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        return result
    }
}
