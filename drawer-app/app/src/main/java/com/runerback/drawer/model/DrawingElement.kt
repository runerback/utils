package com.runerback.drawer.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

sealed class DrawingElement {
    abstract val id: String
    abstract val color: Color
    abstract val strokeWidth: Float
}

data class FreePath(
    override val id: String,
    val points: List<Offset>,
    override val color: Color,
    override val strokeWidth: Float
) : DrawingElement()

data class BezierCurve(
    override val id: String,
    val nodes: List<Offset>,
    override val color: Color,
    override val strokeWidth: Float
) : DrawingElement()

data class Square(
    override val id: String,
    val topLeft: Offset,
    val size: Float,
    override val color: Color,
    override val strokeWidth: Float
) : DrawingElement()

data class RoundedRect(
    override val id: String,
    val topLeft: Offset,
    val size: Float,
    val cornerRadius: Float,
    override val color: Color,
    override val strokeWidth: Float
) : DrawingElement()

data class Ellipse(
    override val id: String,
    val center: Offset,
    val radiusX: Float,
    val radiusY: Float,
    override val color: Color,
    override val strokeWidth: Float
) : DrawingElement()

data class FillArea(
    override val id: String,
    val path: Path,
    val fillColor: Color,
    override val color: Color = fillColor,
    override val strokeWidth: Float = 0f
) : DrawingElement()
