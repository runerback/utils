package com.runerback.translator.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.runerback.translator.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Owns the capture files in `cacheDir/captures/`. The in-app camera screen
 * writes shots into a fixed-name `pending.jpg`; the album picker hands over a
 * content Uri. Both paths get the same normalization (EXIF rotation, long
 * edge capped, black border padding) into `scan-<timestamp>.jpg`, and every
 * older scan is swept.
 */
object CapturedImageStore {

    private const val TAG = "CapturedImageStore"
    private const val MAX_LONG_EDGE = 2000
    private const val JPEG_QUALITY = 90

    /** Uniform black border added around captures, as a fraction of the long
     *  edge, so the OCR range selector can be pushed right up to the picture's
     *  edges on screen. Padding is added to the image itself, never removed. */
    private const val BORDER_FRACTION = 0.05f

    private fun capturesDir(context: Context): File = File(context.cacheDir, "captures")

    /** Fixed-name file the in-app camera writes into via ImageCapture. */
    fun pendingFile(context: Context): File {
        return File(capturesDir(context), "pending.jpg").apply { parentFile?.mkdirs() }
    }

    /**
     * Normalizes the pending camera capture into `scan-<timestamp>.jpg`,
     * deletes the pending file and every older scan, and returns the new scan
     * file. Null when the pending capture is missing or undecodable.
     */
    suspend fun storePendingCapture(context: Context): File? = withContext(Dispatchers.IO) {
        val pending = pendingFile(context)
        if (!pending.exists() || pending.length() <= 0) return@withContext null
        val scan = runCatching {
            val bitmap = decodeNormalized(
                open = { pending.inputStream() },
                exif = { ExifInterface(pending) },
            ) ?: throw IllegalStateException("Undecodable capture ${pending.length()}B")
            finishStore(context, bitmap)
        }.getOrElse { e ->
            LogManager.e(TAG, "Failed to store capture", e)
            null
        }
        pending.delete()
        scan
    }

    /**
     * Same normalization for an image picked from the album (content Uri).
     * The pending file is not involved; older scans are still swept.
     */
    suspend fun storeAlbumImage(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bitmap = decodeNormalized(
                open = { resolver.openInputStream(uri) },
                exif = { resolver.openInputStream(uri)?.use { ExifInterface(it) } },
            ) ?: throw IllegalStateException("Undecodable image $uri")
            finishStore(context, bitmap)
        }.getOrElse { e ->
            LogManager.e(TAG, "Failed to store album image", e)
            null
        }
    }

    /** Pad with the black border, save, sweep older scans; returns the scan file. */
    private fun finishStore(context: Context, bitmap: Bitmap): File {
        val padded = padWithBlackBorder(bitmap)
        val out = File(capturesDir(context), "scan-${System.currentTimeMillis()}.jpg")
        out.outputStream().use { stream ->
            padded.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        }
        LogManager.d(TAG, "stored ${padded.width}x${padded.height} ${out.name}")
        padded.recycle()
        capturesDir(context).listFiles()
            ?.filter { it.name.startsWith("scan-") && it != out }
            ?.forEach { it.delete() }
        return out
    }

    private fun decodeNormalized(
        open: () -> InputStream?,
        exif: () -> ExifInterface?,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val rotation = when (
            exif()?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return scaleToLimit(decoded)

        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return scaleToLimit(rotated)
    }

    private fun padWithBlackBorder(bitmap: Bitmap): Bitmap {
        val border = (maxOf(bitmap.width, bitmap.height) * BORDER_FRACTION).toInt().coerceAtLeast(1)
        val padded = Bitmap.createBitmap(
            bitmap.width + border * 2,
            bitmap.height + border * 2,
            bitmap.config ?: Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(padded)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bitmap, border.toFloat(), border.toFloat(), null)
        bitmap.recycle()
        return padded
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longEdge = maxOf(width, height)
        while (longEdge / 2 >= MAX_LONG_EDGE) {
            sample *= 2
            longEdge /= 2
        }
        return sample
    }

    private fun scaleToLimit(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_LONG_EDGE) return bitmap
        val scale = MAX_LONG_EDGE.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
        bitmap.recycle()
        return scaled
    }
}
