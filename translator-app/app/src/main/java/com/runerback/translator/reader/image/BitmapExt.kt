package com.runerback.translator.reader.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.runerback.translator.util.LogManager

fun Bitmap.cropWhitespace(threshold: Int = 250, debug: Boolean = false): Bitmap {
    val pixelCount = width.toLong() * height
    if (pixelCount <= 0 || pixelCount > Int.MAX_VALUE) return this
    val pixels = IntArray(pixelCount.toInt())
    getPixels(pixels, 0, width, 0, 0, width, height)

    val borderLuminances = sampleBorderLuminances(pixels, width, height)
    if (borderLuminances.isEmpty()) {
        if (debug) LogManager.d("BitmapExt", "no border samples, returning original")
        return this
    }

    val sortedLuminances = borderLuminances.sorted()
    val backgroundLum = sortedLuminances[(sortedLuminances.size * 0.9).toInt().coerceIn(0, sortedLuminances.lastIndex)]
    val isLightBackground = backgroundLum > 127.5
    // The threshold parameter was originally a white-channel minimum. For adaptive
    // background detection a narrow luminance band around the detected background
    // works much better; use 25 as the default +/- tolerance.
    val tolerance = 25.0
    if (debug) {
        LogManager.d(
            "BitmapExt",
            "bitmap=${width}x${height} backgroundLum=$backgroundLum light=$isLightBackground tolerance=$tolerance",
        )
    }

    fun isBackground(pixel: Int): Boolean {
        if (Color.alpha(pixel) < 10) return true
        val lum = pixelLuminance(pixel)
        return if (isLightBackground) {
            lum >= backgroundLum - tolerance
        } else {
            lum <= backgroundLum + tolerance
        }
    }

    fun rowBackgroundRatio(y: Int): Double {
        val offset = y * width
        var backgroundCount = 0
        for (x in 0 until width) {
            if (isBackground(pixels[offset + x])) backgroundCount++
        }
        return backgroundCount.toDouble() / width
    }

    fun colBackgroundRatio(x: Int): Double {
        var backgroundCount = 0
        for (y in 0 until height) {
            if (isBackground(pixels[y * width + x])) backgroundCount++
        }
        return backgroundCount.toDouble() / height
    }

    fun rowEmpty(y: Int): Boolean = rowBackgroundRatio(y) >= 0.995
    fun colEmpty(x: Int): Boolean = colBackgroundRatio(x) >= 0.995

    // Find the tight bounding box of the page content.
    var top = 0
    while (top < height && rowEmpty(top)) top++
    var bottom = height - 1
    while (bottom > top && rowEmpty(bottom)) bottom--
    var left = 0
    while (left < width && colEmpty(left)) left++
    var right = width - 1
    while (right > left && colEmpty(right)) right--

    if (debug) {
        LogManager.d(
            "BitmapExt",
            "bounds scan top=$top bottom=$bottom left=$left right=$right " +
                "topBg=${rowBackgroundRatio(0).format()} " +
                "bottomBg=${rowBackgroundRatio(height - 1).format()} " +
                "leftBg=${colBackgroundRatio(0).format()} " +
                "rightBg=${colBackgroundRatio(width - 1).format()}",
        )
    }

    if (top == 0 && bottom == height - 1 && left == 0 && right == width - 1) {
        if (debug) LogManager.d("BitmapExt", "no whitespace detected, returning original")
        return this
    }

    val contentWidth = right - left + 1
    val contentHeight = bottom - top + 1
    if (contentWidth <= 0 || contentHeight <= 0) {
        if (debug) LogManager.d("BitmapExt", "invalid content size, returning original")
        return this
    }

    val contentArea = contentWidth.toLong() * contentHeight
    val pageArea = width.toLong() * height
    if (contentArea < pageArea * 0.10) {
        if (debug) {
            LogManager.d(
                "BitmapExt",
                "content too small: content=$contentArea page=$pageArea ratio=${contentArea.toDouble() / pageArea}, returning original",
            )
        }
        return this
    }

    if (debug) {
        LogManager.d(
            "BitmapExt",
            "cropping content=[$left,$top,$right,$bottom] " +
                "size=${contentWidth}x${contentHeight}",
        )
    }

    val cropped = Bitmap.createBitmap(this, left, top, contentWidth, contentHeight)

    if (debug) {
        val debugBitmap = cropped.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(debugBitmap)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(contentWidth, contentHeight) * 0.03f
            color = Color.BLUE
        }
        canvas.drawRect(0f, 0f, contentWidth.toFloat(), contentHeight.toFloat(), paint)
        return debugBitmap
    }

    return cropped
}

private fun Double.format(): String = String.format("%.3f", this)

private fun sampleBorderLuminances(pixels: IntArray, width: Int, height: Int): List<Double> {
    val borderWidth = (width * 0.05f).toInt().coerceAtLeast(1)
    val borderHeight = (height * 0.05f).toInt().coerceAtLeast(1)
    val samples = mutableListOf<Double>()

    for (y in 0 until height) {
        for (x in 0 until borderWidth) {
            samples.add(pixelLuminance(pixels[y * width + x]))
            samples.add(pixelLuminance(pixels[y * width + (width - 1 - x)]))
        }
    }
    for (x in borderWidth until (width - borderWidth)) {
        for (y in 0 until borderHeight) {
            samples.add(pixelLuminance(pixels[y * width + x]))
            samples.add(pixelLuminance(pixels[(height - 1 - y) * width + x]))
        }
    }

    return samples
}

private fun pixelLuminance(pixel: Int): Double {
    return (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3.0
}
