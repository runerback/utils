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

val FluentuiSystemIconsSelectAllOff: ImageVector
    get() {
        if (_FluentuiSystemIconsSelectAllOff != null) return _FluentuiSystemIconsSelectAllOff!!
        
        _FluentuiSystemIconsSelectAllOff = ImageVector.Builder(
            name = "select-all-off",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(6.25f, 3f)
                curveTo(4.45507f, 3f, 3f, 4.45507f, 3f, 6.25f)
                verticalLineTo(15.25f)
                curveTo(3f, 17.0449f, 4.45507f, 18.5f, 6.25f, 18.5f)
                horizontalLineTo(15.25f)
                curveTo(17.0449f, 18.5f, 18.5f, 17.0449f, 18.5f, 15.25f)
                verticalLineTo(6.25f)
                curveTo(18.5f, 4.45507f, 17.0449f, 3f, 15.25f, 3f)
                horizontalLineTo(6.25f)
                close()
                moveTo(4.5f, 6.25f)
                curveTo(4.5f, 5.2835f, 5.2835f, 4.5f, 6.25f, 4.5f)
                horizontalLineTo(15.25f)
                curveTo(16.2165f, 4.5f, 17f, 5.2835f, 17f, 6.25f)
                verticalLineTo(15.25f)
                curveTo(17f, 16.2165f, 16.2165f, 17f, 15.25f, 17f)
                horizontalLineTo(6.25f)
                curveTo(5.2835f, 17f, 4.5f, 16.2165f, 4.5f, 15.25f)
                verticalLineTo(6.25f)
                close()
                moveTo(6.01074f, 19.5f)
                curveTo(6.58826f, 20.4022f, 7.59927f, 21.0002f, 8.74995f, 21.0002f)
                horizontalLineTo(15.7499f)
                curveTo(18.6494f, 21.0002f, 21f, 18.6497f, 21f, 15.7502f)
                verticalLineTo(8.75017f)
                curveTo(21f, 7.59956f, 20.402f, 6.58861f, 19.5f, 6.01108f)
                verticalLineTo(15.7502f)
                curveTo(19.5f, 17.8212f, 17.821f, 19.5002f, 15.7499f, 19.5002f)
                horizontalLineTo(8.74995f)
                lineTo(8.72444f, 19.5f)
                horizontalLineTo(6.01074f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsSelectAllOff!!
    }

private var _FluentuiSystemIconsSelectAllOff: ImageVector? = null