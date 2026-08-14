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
package com.runerback.comfyuiapi.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PhosphorQueue: ImageVector
    get() {
        if (_PhosphorQueue != null) return _PhosphorQueue!!
        
        _PhosphorQueue = ImageVector.Builder(
            name = "queue",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(32f, 64f)
                arcToRelative(8f, 8f, 0f, false, true, 8f, -8f)
                horizontalLineTo(216f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, 16f)
                horizontalLineTo(40f)
                arcTo(8f, 8f, 0f, false, true, 32f, 64f)
                close()
                moveToRelative(104f, 56f)
                horizontalLineTo(40f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineToRelative(96f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                close()
                moveToRelative(0f, 64f)
                horizontalLineTo(40f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineToRelative(96f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                close()
                moveToRelative(112f, -24f)
                arcToRelative(8f, 8f, 0f, false, true, -3.76f, 6.78f)
                lineToRelative(-64f, 40f)
                arcTo(8f, 8f, 0f, false, true, 168f, 200f)
                verticalLineTo(120f)
                arcToRelative(8f, 8f, 0f, false, true, 12.24f, -6.78f)
                lineToRelative(64f, 40f)
                arcTo(8f, 8f, 0f, false, true, 248f, 160f)
                close()
                moveToRelative(-23.09f, 0f)
                lineTo(184f, 134.43f)
                verticalLineToRelative(51.14f)
                close()
            }
        }.build()
        
        return _PhosphorQueue!!
    }

private var _PhosphorQueue: ImageVector? = null