package com.runerback.translator.reader.image

import android.graphics.Bitmap
import android.graphics.Color

fun Bitmap.cropWhitespace(threshold: Int = 250): Bitmap {
    val pixelCount = width.toLong() * height
    if (pixelCount <= 0 || pixelCount > Int.MAX_VALUE) return this
    val pixels = IntArray(pixelCount.toInt())
    getPixels(pixels, 0, width, 0, 0, width, height)

    fun isEmpty(pixel: Int): Boolean {
        return Color.red(pixel) >= threshold &&
            Color.green(pixel) >= threshold &&
            Color.blue(pixel) >= threshold
    }

    fun rowEmpty(y: Int): Boolean {
        val offset = y * width
        for (x in 0 until width) {
            if (!isEmpty(pixels[offset + x])) return false
        }
        return true
    }

    fun colEmpty(x: Int): Boolean {
        for (y in 0 until height) {
            if (!isEmpty(pixels[y * width + x])) return false
        }
        return true
    }

    var top = 0
    while (top < height && rowEmpty(top)) top++
    var bottom = height - 1
    while (bottom > top && rowEmpty(bottom)) bottom--
    var left = 0
    while (left < width && colEmpty(left)) left++
    var right = width - 1
    while (right > left && colEmpty(right)) right--

    if (top == 0 && bottom == height - 1 && left == 0 && right == width - 1) {
        return this
    }

    val newWidth = right - left + 1
    val newHeight = bottom - top + 1
    if (newWidth <= 0 || newHeight <= 0) return this

    return Bitmap.createBitmap(this, left, top, newWidth, newHeight)
}
