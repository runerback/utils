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

val FluentuiSystemIconsForm: ImageVector
    get() {
        if (_FluentuiSystemIconsForm != null) return _FluentuiSystemIconsForm!!
        
        _FluentuiSystemIconsForm = ImageVector.Builder(
            name = "form",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(6f, 10.75f)
                curveTo(6f, 9.50736f, 7.00736f, 8.5f, 8.25f, 8.5f)
                curveTo(9.49264f, 8.5f, 10.5f, 9.50736f, 10.5f, 10.75f)
                curveTo(10.5f, 11.9926f, 9.49264f, 13f, 8.25f, 13f)
                curveTo(7.00736f, 13f, 6f, 11.9926f, 6f, 10.75f)
                close()
                moveTo(8.25f, 10f)
                curveTo(7.83579f, 10f, 7.5f, 10.3358f, 7.5f, 10.75f)
                curveTo(7.5f, 11.1642f, 7.83579f, 11.5f, 8.25f, 11.5f)
                curveTo(8.66421f, 11.5f, 9f, 11.1642f, 9f, 10.75f)
                curveTo(9f, 10.3358f, 8.66421f, 10f, 8.25f, 10f)
                close()
                moveTo(8.25f, 14f)
                curveTo(7.00736f, 14f, 6f, 15.0074f, 6f, 16.25f)
                curveTo(6f, 17.4926f, 7.00736f, 18.5f, 8.25f, 18.5f)
                curveTo(9.49264f, 18.5f, 10.5f, 17.4926f, 10.5f, 16.25f)
                curveTo(10.5f, 15.0074f, 9.49264f, 14f, 8.25f, 14f)
                close()
                moveTo(7.5f, 16.25f)
                curveTo(7.5f, 15.8358f, 7.83579f, 15.5f, 8.25f, 15.5f)
                curveTo(8.66421f, 15.5f, 9f, 15.8358f, 9f, 16.25f)
                curveTo(9f, 16.6642f, 8.66421f, 17f, 8.25f, 17f)
                curveTo(7.83579f, 17f, 7.5f, 16.6642f, 7.5f, 16.25f)
                close()
                moveTo(12f, 10.75f)
                curveTo(12f, 10.3358f, 12.3358f, 10f, 12.75f, 10f)
                horizontalLineTo(17.25f)
                curveTo(17.6642f, 10f, 18f, 10.3358f, 18f, 10.75f)
                curveTo(18f, 11.1642f, 17.6642f, 11.5f, 17.25f, 11.5f)
                horizontalLineTo(12.75f)
                curveTo(12.3358f, 11.5f, 12f, 11.1642f, 12f, 10.75f)
                close()
                moveTo(12.75f, 15.5f)
                curveTo(12.3358f, 15.5f, 12f, 15.8358f, 12f, 16.25f)
                curveTo(12f, 16.6642f, 12.3358f, 17f, 12.75f, 17f)
                horizontalLineTo(17.25f)
                curveTo(17.6642f, 17f, 18f, 16.6642f, 18f, 16.25f)
                curveTo(18f, 15.8358f, 17.6642f, 15.5f, 17.25f, 15.5f)
                horizontalLineTo(12.75f)
                close()
                moveTo(6f, 6.75f)
                curveTo(6f, 6.33579f, 6.33579f, 6f, 6.75f, 6f)
                horizontalLineTo(17.25f)
                curveTo(17.6642f, 6f, 18f, 6.33579f, 18f, 6.75f)
                curveTo(18f, 7.16421f, 17.6642f, 7.5f, 17.25f, 7.5f)
                horizontalLineTo(6.75f)
                curveTo(6.33579f, 7.5f, 6f, 7.16421f, 6f, 6.75f)
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
        
        return _FluentuiSystemIconsForm!!
    }

private var _FluentuiSystemIconsForm: ImageVector? = null