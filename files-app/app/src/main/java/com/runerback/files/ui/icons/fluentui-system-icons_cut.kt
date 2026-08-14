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

val FluentuiSystemIconsCut: ImageVector
    get() {
        if (_FluentuiSystemIconsCut != null) return _FluentuiSystemIconsCut!!
        
        _FluentuiSystemIconsCut = ImageVector.Builder(
            name = "cut",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(12.1409f, 9.3415f)
                lineTo(12.14f, 9.34286f)
                lineTo(7.37017f, 2.3284f)
                curveTo(7.13725f, 1.98587f, 6.67077f, 1.89702f, 6.32824f, 2.12994f)
                curveTo(5.98572f, 2.36286f, 5.89687f, 2.82934f, 6.12978f, 3.17187f)
                lineTo(11.2606f, 10.7171f)
                lineTo(8.86478f, 14.4605f)
                curveTo(8.30797f, 14.1665f, 7.67342f, 14.0001f, 7f, 14.0001f)
                curveTo(4.79086f, 14.0001f, 3f, 15.791f, 3f, 18.0001f)
                curveTo(3f, 20.2093f, 4.79086f, 22.0001f, 7f, 22.0001f)
                curveTo(9.20914f, 22.0001f, 11f, 20.2093f, 11f, 18.0001f)
                curveTo(11f, 17.0089f, 10.6395f, 16.1019f, 10.0424f, 15.4031f)
                lineTo(12.178f, 12.0662f)
                lineTo(14.2426f, 15.1024f)
                curveTo(13.4771f, 15.831f, 13f, 16.8599f, 13f, 18.0001f)
                curveTo(13f, 20.2093f, 14.7909f, 22.0001f, 17f, 22.0001f)
                curveTo(19.2091f, 22.0001f, 21f, 20.2093f, 21f, 18.0001f)
                curveTo(21f, 15.791f, 19.2091f, 14.0001f, 17f, 14.0001f)
                curveTo(16.471f, 14.0001f, 15.9659f, 14.1028f, 15.5037f, 14.2894f)
                lineTo(13.0575f, 10.692f)
                lineTo(13.0588f, 10.69f)
                lineTo(12.1409f, 9.3415f)
                close()
                moveTo(4.5f, 18.0001f)
                curveTo(4.5f, 16.6194f, 5.61929f, 15.5001f, 7f, 15.5001f)
                curveTo(8.38071f, 15.5001f, 9.5f, 16.6194f, 9.5f, 18.0001f)
                curveTo(9.5f, 19.3808f, 8.38071f, 20.5001f, 7f, 20.5001f)
                curveTo(5.61929f, 20.5001f, 4.5f, 19.3808f, 4.5f, 18.0001f)
                close()
                moveTo(14.5f, 18.0001f)
                curveTo(14.5f, 16.6194f, 15.6193f, 15.5001f, 17f, 15.5001f)
                curveTo(18.3807f, 15.5001f, 19.5f, 16.6194f, 19.5f, 18.0001f)
                curveTo(19.5f, 19.3808f, 18.3807f, 20.5001f, 17f, 20.5001f)
                curveTo(15.6193f, 20.5001f, 14.5f, 19.3808f, 14.5f, 18.0001f)
                close()
                moveTo(13.9381f, 9.31607f)
                lineTo(17.8815f, 3.15438f)
                curveTo(18.1048f, 2.8055f, 18.003f, 2.34167f, 17.6541f, 2.11839f)
                curveTo(17.3053f, 1.89511f, 16.8414f, 1.99692f, 16.6181f, 2.3458f)
                lineTo(13.0202f, 7.96756f)
                lineTo(13.9381f, 9.31607f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsCut!!
    }

private var _FluentuiSystemIconsCut: ImageVector? = null