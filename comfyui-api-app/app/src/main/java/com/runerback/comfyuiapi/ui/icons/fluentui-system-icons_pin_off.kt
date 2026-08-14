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
package com.runerback.comfyuiapi.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FluentuiSystemIconsPinOff: ImageVector
    get() {
        if (_FluentuiSystemIconsPinOff != null) return _FluentuiSystemIconsPinOff!!
        
        _FluentuiSystemIconsPinOff = ImageVector.Builder(
            name = "pin-off",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(3.28035f, 2.21967f)
                curveTo(2.98746f, 1.92678f, 2.51258f, 1.92677f, 2.21968f, 2.21966f)
                curveTo(1.92678f, 2.51255f, 1.92677f, 2.98742f, 2.21967f, 3.28032f)
                lineTo(8.12461f, 9.18526f)
                lineTo(4.81064f, 10.3301f)
                curveTo(3.93794f, 10.6315f, 3.68202f, 11.7426f, 4.3349f, 12.3954f)
                lineTo(7.43942f, 15.4999f)
                lineTo(3.00009f, 19.9392f)
                lineTo(3.00001f, 20.9999f)
                horizontalLineTo(4.06077f)
                lineTo(8.50009f, 16.5605f)
                lineTo(11.6046f, 19.665f)
                curveTo(12.2575f, 20.3179f, 13.3686f, 20.062f, 13.67f, 19.1893f)
                lineTo(14.8148f, 15.8755f)
                lineTo(20.7196f, 21.7803f)
                curveTo(21.0125f, 22.0732f, 21.4874f, 22.0732f, 21.7803f, 21.7803f)
                curveTo(22.0732f, 21.4874f, 22.0732f, 21.0126f, 21.7803f, 20.7197f)
                lineTo(3.28035f, 2.21967f)
                close()
                moveTo(13.6353f, 14.696f)
                lineTo(12.3827f, 18.3218f)
                lineTo(5.67816f, 11.6174f)
                lineTo(9.30412f, 10.3648f)
                lineTo(13.6353f, 14.696f)
                close()
                moveTo(19.6829f, 10.8201f)
                lineTo(15.8957f, 12.7137f)
                lineTo(17.0137f, 13.8317f)
                lineTo(20.3537f, 12.1617f)
                curveTo(22.0326f, 11.3223f, 22.3957f, 9.08476f, 21.0684f, 7.75751f)
                lineTo(16.2426f, 2.93179f)
                curveTo(14.9153f, 1.60453f, 12.6778f, 1.96763f, 11.8383f, 3.6465f)
                lineTo(10.1684f, 6.98637f)
                lineTo(11.2864f, 8.10441f)
                lineTo(13.18f, 4.31731f)
                curveTo(13.5616f, 3.55419f, 14.5786f, 3.38914f, 15.1819f, 3.99244f)
                lineTo(20.0078f, 8.81817f)
                curveTo(20.6111f, 9.42146f, 20.446f, 10.4385f, 19.6829f, 10.8201f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsPinOff!!
    }

private var _FluentuiSystemIconsPinOff: ImageVector? = null