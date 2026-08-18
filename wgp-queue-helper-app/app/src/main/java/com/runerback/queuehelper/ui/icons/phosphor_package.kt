package com.runerback.queuehelper.ui.icons

/*
MIT License

Copyright (c) 2020 Phosphor Icons

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PhosphorPackage: ImageVector
    get() {
        if (_PhosphorPackage != null) return _PhosphorPackage!!
        
        _PhosphorPackage = ImageVector.Builder(
            name = "package",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(223.68f, 66.15f)
                lineTo(135.68f, 18f)
                arcToRelative(15.88f, 15.88f, 0f, false, false, -15.36f, 0f)
                lineToRelative(-88f, 48.17f)
                arcToRelative(16f, 16f, 0f, false, false, -8.32f, 14f)
                verticalLineToRelative(95.64f)
                arcToRelative(16f, 16f, 0f, false, false, 8.32f, 14f)
                lineToRelative(88f, 48.17f)
                arcToRelative(15.88f, 15.88f, 0f, false, false, 15.36f, 0f)
                lineToRelative(88f, -48.17f)
                arcToRelative(16f, 16f, 0f, false, false, 8.32f, -14f)
                verticalLineTo(80.18f)
                arcTo(16f, 16f, 0f, false, false, 223.68f, 66.15f)
                close()
                moveTo(128f, 32f)
                lineToRelative(80.34f, 44f)
                lineToRelative(-29.77f, 16.3f)
                lineToRelative(-80.35f, -44f)
                close()
                moveTo(128f, 120f)
                lineTo(47.66f, 76f)
                lineToRelative(33.9f, -18.56f)
                lineToRelative(80.34f, 44f)
                close()
                moveTo(40f, 90f)
                lineToRelative(80f, 43.78f)
                verticalLineToRelative(85.79f)
                lineTo(40f, 175.82f)
                close()
                moveToRelative(176f, 85.78f)
                horizontalLineToRelative(0f)
                lineToRelative(-80f, 43.79f)
                verticalLineTo(133.82f)
                lineToRelative(32f, -17.51f)
                verticalLineTo(152f)
                arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                verticalLineTo(107.55f)
                lineTo(216f, 90f)
                verticalLineToRelative(85.77f)
                close()
            }
        }.build()
        
        return _PhosphorPackage!!
    }

private var _PhosphorPackage: ImageVector? = null