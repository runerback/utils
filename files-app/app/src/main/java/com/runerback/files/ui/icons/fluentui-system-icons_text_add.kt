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

val FluentuiSystemIconsTextAdd: ImageVector
    get() {
        if (_FluentuiSystemIconsTextAdd != null) return _FluentuiSystemIconsTextAdd!!
        
        _FluentuiSystemIconsTextAdd = ImageVector.Builder(
            name = "text-add",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 20f,
            viewportHeight = 20f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3f, 3.5f)
                curveTo(3f, 3.22386f, 3.22386f, 3f, 3.5f, 3f)
                horizontalLineTo(13.5f)
                curveTo(13.7761f, 3f, 14f, 3.22386f, 14f, 3.5f)
                verticalLineTo(5.5f)
                curveTo(14f, 5.77614f, 13.7761f, 6f, 13.5f, 6f)
                curveTo(13.2239f, 6f, 13f, 5.77614f, 13f, 5.5f)
                verticalLineTo(4f)
                horizontalLineTo(9f)
                verticalLineTo(16f)
                horizontalLineTo(9.20703f)
                curveTo(9.30564f, 16.3486f, 9.43777f, 16.6832f, 9.59971f, 17f)
                horizontalLineTo(6.5f)
                curveTo(6.22386f, 17f, 6f, 16.7761f, 6f, 16.5f)
                curveTo(6f, 16.2239f, 6.22386f, 16f, 6.5f, 16f)
                horizontalLineTo(8f)
                verticalLineTo(4f)
                horizontalLineTo(4f)
                verticalLineTo(5.5f)
                curveTo(4f, 5.77614f, 3.77614f, 6f, 3.5f, 6f)
                curveTo(3.22386f, 6f, 3f, 5.77614f, 3f, 5.5f)
                verticalLineTo(3.5f)
                close()
                moveTo(19f, 14.5f)
                curveTo(19f, 16.9853f, 16.9853f, 19f, 14.5f, 19f)
                curveTo(12.0147f, 19f, 10f, 16.9853f, 10f, 14.5f)
                curveTo(10f, 12.0147f, 12.0147f, 10f, 14.5f, 10f)
                curveTo(16.9853f, 10f, 19f, 12.0147f, 19f, 14.5f)
                close()
                moveTo(15f, 12.5f)
                curveTo(15f, 12.2239f, 14.7761f, 12f, 14.5f, 12f)
                curveTo(14.2239f, 12f, 14f, 12.2239f, 14f, 12.5f)
                verticalLineTo(14f)
                horizontalLineTo(12.5f)
                curveTo(12.2239f, 14f, 12f, 14.2239f, 12f, 14.5f)
                curveTo(12f, 14.7761f, 12.2239f, 15f, 12.5f, 15f)
                horizontalLineTo(14f)
                verticalLineTo(16.5f)
                curveTo(14f, 16.7761f, 14.2239f, 17f, 14.5f, 17f)
                curveTo(14.7761f, 17f, 15f, 16.7761f, 15f, 16.5f)
                verticalLineTo(15f)
                horizontalLineTo(16.5f)
                curveTo(16.7761f, 15f, 17f, 14.7761f, 17f, 14.5f)
                curveTo(17f, 14.2239f, 16.7761f, 14f, 16.5f, 14f)
                horizontalLineTo(15f)
                verticalLineTo(12.5f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsTextAdd!!
    }

private var _FluentuiSystemIconsTextAdd: ImageVector? = null