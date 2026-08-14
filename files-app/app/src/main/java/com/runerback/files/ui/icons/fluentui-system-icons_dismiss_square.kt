/*
MIT License

Copyright (c) 2020 Microsoft Corporation

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
package com.runerback.files.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FluentuiSystemIconsDismissSquare: ImageVector
    get() {
        if (_FluentuiSystemIconsDismissSquare != null) return _FluentuiSystemIconsDismissSquare!!
        
        _FluentuiSystemIconsDismissSquare = ImageVector.Builder(
            name = "dismiss-square",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(8.21967f, 8.21967f)
                curveTo(8.51256f, 7.92678f, 8.98744f, 7.92678f, 9.28033f, 8.21967f)
                lineTo(12f, 10.9393f)
                lineTo(14.7197f, 8.21967f)
                curveTo(15.0126f, 7.92678f, 15.4874f, 7.92678f, 15.7803f, 8.21967f)
                curveTo(16.0732f, 8.51256f, 16.0732f, 8.98744f, 15.7803f, 9.28033f)
                lineTo(13.0607f, 12f)
                lineTo(15.7803f, 14.7197f)
                curveTo(16.0732f, 15.0126f, 16.0732f, 15.4874f, 15.7803f, 15.7803f)
                curveTo(15.4874f, 16.0732f, 15.0126f, 16.0732f, 14.7197f, 15.7803f)
                lineTo(12f, 13.0607f)
                lineTo(9.28033f, 15.7803f)
                curveTo(8.98744f, 16.0732f, 8.51256f, 16.0732f, 8.21967f, 15.7803f)
                curveTo(7.92678f, 15.4874f, 7.92678f, 15.0126f, 8.21967f, 14.7197f)
                lineTo(10.9393f, 12f)
                lineTo(8.21967f, 9.28033f)
                curveTo(7.92678f, 8.98744f, 7.92678f, 8.51256f, 8.21967f, 8.21967f)
                close()
                moveTo(6.25f, 3f)
                curveTo(4.45507f, 3f, 3f, 4.45507f, 3f, 6.25f)
                verticalLineTo(17.75f)
                curveTo(3f, 19.5449f, 4.45507f, 21f, 6.25f, 21f)
                horizontalLineTo(17.75f)
                curveTo(19.5449f, 21f, 21f, 19.5449f, 21f, 17.75f)
                verticalLineTo(6.25f)
                curveTo(21f, 4.45507f, 19.5449f, 3f, 17.75f, 3f)
                horizontalLineTo(6.25f)
                close()
                moveTo(4.5f, 6.25f)
                curveTo(4.5f, 5.2835f, 5.2835f, 4.5f, 6.25f, 4.5f)
                horizontalLineTo(17.75f)
                curveTo(18.7165f, 4.5f, 19.5f, 5.2835f, 19.5f, 6.25f)
                verticalLineTo(17.75f)
                curveTo(19.5f, 18.7165f, 18.7165f, 19.5f, 17.75f, 19.5f)
                horizontalLineTo(6.25f)
                curveTo(5.2835f, 19.5f, 4.5f, 18.7165f, 4.5f, 17.75f)
                verticalLineTo(6.25f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsDismissSquare!!
    }

private var _FluentuiSystemIconsDismissSquare: ImageVector? = null