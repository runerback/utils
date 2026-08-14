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

val FluentuiSystemIconsDismissSquareMultiple: ImageVector
    get() {
        if (_FluentuiSystemIconsDismissSquareMultiple != null) return _FluentuiSystemIconsDismissSquareMultiple!!
        
        _FluentuiSystemIconsDismissSquareMultiple = ImageVector.Builder(
            name = "dismiss-square-multiple",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 20f,
            viewportHeight = 20f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3f, 5.5f)
                curveTo(3f, 4.11929f, 4.11929f, 3f, 5.5f, 3f)
                horizontalLineTo(12.5f)
                curveTo(13.8807f, 3f, 15f, 4.11929f, 15f, 5.5f)
                verticalLineTo(12.5f)
                curveTo(15f, 13.8807f, 13.8807f, 15f, 12.5f, 15f)
                horizontalLineTo(5.5f)
                curveTo(4.11929f, 15f, 3f, 13.8807f, 3f, 12.5f)
                verticalLineTo(5.5f)
                close()
                moveTo(5.5f, 4f)
                curveTo(4.67157f, 4f, 4f, 4.67157f, 4f, 5.5f)
                verticalLineTo(12.5f)
                curveTo(4f, 13.3284f, 4.67157f, 14f, 5.5f, 14f)
                horizontalLineTo(12.5f)
                curveTo(13.3284f, 14f, 14f, 13.3284f, 14f, 12.5f)
                verticalLineTo(5.5f)
                curveTo(14f, 4.67157f, 13.3284f, 4f, 12.5f, 4f)
                horizontalLineTo(5.5f)
                close()
                moveTo(7.50018f, 17.0002f)
                curveTo(6.68227f, 17.0002f, 5.9561f, 16.6074f, 5.5f, 16.0002f)
                horizontalLineTo(12.5002f)
                curveTo(14.4332f, 16.0002f, 16.0002f, 14.4332f, 16.0002f, 12.5002f)
                verticalLineTo(5.50018f)
                curveTo(16.6074f, 5.95628f, 17.0002f, 6.68227f, 17.0002f, 7.50018f)
                verticalLineTo(12.5002f)
                curveTo(17.0002f, 14.9855f, 14.9855f, 17.0002f, 12.5002f, 17.0002f)
                horizontalLineTo(7.50018f)
                close()
                moveTo(6.85355f, 6.14645f)
                curveTo(6.65829f, 5.95118f, 6.34171f, 5.95118f, 6.14645f, 6.14645f)
                curveTo(5.95118f, 6.34171f, 5.95118f, 6.65829f, 6.14645f, 6.85355f)
                lineTo(8.29289f, 9f)
                lineTo(6.14645f, 11.1464f)
                curveTo(5.95118f, 11.3417f, 5.95118f, 11.6583f, 6.14645f, 11.8536f)
                curveTo(6.34171f, 12.0488f, 6.65829f, 12.0488f, 6.85355f, 11.8536f)
                lineTo(9f, 9.70711f)
                lineTo(11.1464f, 11.8536f)
                curveTo(11.3417f, 12.0488f, 11.6583f, 12.0488f, 11.8536f, 11.8536f)
                curveTo(12.0488f, 11.6583f, 12.0488f, 11.3417f, 11.8536f, 11.1464f)
                lineTo(9.70711f, 9f)
                lineTo(11.8536f, 6.85355f)
                curveTo(12.0488f, 6.65829f, 12.0488f, 6.34171f, 11.8536f, 6.14645f)
                curveTo(11.6583f, 5.95118f, 11.3417f, 5.95118f, 11.1464f, 6.14645f)
                lineTo(9f, 8.29289f)
                lineTo(6.85355f, 6.14645f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsDismissSquareMultiple!!
    }

private var _FluentuiSystemIconsDismissSquareMultiple: ImageVector? = null