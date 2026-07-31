package com.runerback.translator.reader.image

import android.graphics.Bitmap
import android.graphics.Color

fun Bitmap.cropWhitespace(threshold: Int = 250): Bitmap {
    val pixelCount = width.toLong() * height
    if (pixelCount <= 0 || pixelCount > Int.MAX_VALUE) return this
    val pixels = IntArray(pixelCount.toInt())
    getPixels(pixels, 0, width, 0, 0, width, height)

    val borderLuminances = sampleBorderLuminances(pixels, width, height)
    if (borderLuminances.isEmpty()) return this

    val sortedLuminances = borderLuminances.sorted()
    val backgroundLum = sortedLuminances[(sortedLuminances.size * 0.9).toInt().coerceIn(0, sortedLuminances.lastIndex)]
    val isLightBackground = backgroundLum > 127.5
    val tolerance = threshold.toDouble()

    fun isBackground(pixel: Int): Boolean {
        if (Color.alpha(pixel) < 10) return true
        val lum = pixelLuminance(pixel)
        return if (isLightBackground) {
            lum >= backgroundLum - tolerance
        } else {
            lum <= backgroundLum + tolerance
        }
    }

    fun rowEmpty(y: Int): Boolean {
        val offset = y * width
        var backgroundCount = 0
        for (x in 0 until width) {
            if (isBackground(pixels[offset + x])) backgroundCount++
        }
        return backgroundCount.toDouble() / width >= 0.995
    }

    fun colEmpty(x: Int): Boolean {
        var backgroundCount = 0
        for (y in 0 until height) {
            if (isBackground(pixels[y * width + x])) backgroundCount++
        }
        return backgroundCount.toDouble() / height >= 0.995
    }

    val maxTop = (height * 0.20).toInt()
    val maxBottom = height - 1 - maxTop
    val maxLeft = (width * 0.20).toInt()
    val maxRight = width - 1 - maxLeft

    var top = 0
    while (top < maxTop && rowEmpty(top)) top++
    var bottom = height - 1
    while (bottom > maxBottom && rowEmpty(bottom)) bottom--
    var left = 0
    while (left < maxLeft && colEmpty(left)) left++
    var right = width - 1
    while (right > maxRight && colEmpty(right)) right--

    if (top == 0 && bottom == height - 1 && left == 0 && right == width - 1) {
        return this
    }

    val newWidth = right - left + 1
    val newHeight = bottom - top + 1
    if (newWidth <= 0 || newHeight <= 0) return this

    return Bitmap.createBitmap(this, left, top, newWidth, newHeight)
}

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
