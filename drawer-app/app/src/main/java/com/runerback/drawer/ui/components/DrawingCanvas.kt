package com.runerback.drawer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.runerback.drawer.model.BezierCurve
import com.runerback.drawer.model.DrawingElement
import com.runerback.drawer.model.Ellipse
import com.runerback.drawer.model.FillArea
import com.runerback.drawer.model.FreePath
import com.runerback.drawer.model.RoundedRect
import com.runerback.drawer.model.Square
import com.runerback.drawer.ui.viewmodel.DrawerState
import com.runerback.drawer.ui.viewmodel.Tool
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun DrawingCanvas(
    state: DrawerState,
    canDrawOnSelectedLayer: Boolean,
    onPenDrag: (Offset) -> Unit,
    onPenDragEnd: () -> Unit,
    onShapeStart: (Offset) -> Unit,
    onShapeMove: (Offset) -> Unit,
    onShapeEnd: () -> Unit,
    onBezierTap: (Offset) -> Unit,
    onFillTap: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.currentTool, canDrawOnSelectedLayer) {
                if (!canDrawOnSelectedLayer) return@pointerInput
                when (state.currentTool) {
                    Tool.PEN -> {
                        detectDragGestures(
                            onDragStart = { onPenDrag(it) },
                            onDrag = { change, _ -> onPenDrag(change.position) },
                            onDragEnd = { onPenDragEnd() }
                        )
                    }
                    Tool.SQUARE, Tool.ROUNDED_RECT, Tool.ELLIPSE -> {
                        detectDragGestures(
                            onDragStart = { onShapeStart(it) },
                            onDrag = { change, _ -> onShapeMove(change.position) },
                            onDragEnd = { onShapeEnd() }
                        )
                    }
                    Tool.BEZIER -> {
                        detectTapGestures { offset -> onBezierTap(offset) }
                    }
                    Tool.FILL -> {
                        detectTapGestures { offset -> onFillTap(offset) }
                    }
                }
            }
    ) {
        state.layers.forEach { layer ->
            if (layer.isVisible) {
                layer.elementIds.forEach { id ->
                    state.elements[id]?.let { drawElement(it) }
                }
            }
        }

        if (state.inProgressPath.isNotEmpty()) {
            drawFreePath(state.inProgressPath, state.currentColor, state.currentStrokeWidth)
        }

        val start = state.inProgressShapeStart
        val end = state.inProgressShapeEnd
        if (start != null && end != null) {
            val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
            val width = abs(end.x - start.x)
            val height = abs(end.y - start.y)
            when (state.currentTool) {
                Tool.SQUARE -> {
                    val size = max(width, height)
                    drawRect(
                        color = state.currentColor,
                        topLeft = topLeft,
                        size = Size(size, size),
                        style = Stroke(width = state.currentStrokeWidth)
                    )
                }
                Tool.ROUNDED_RECT -> {
                    val size = max(width, height)
                    drawRoundRect(
                        color = state.currentColor,
                        topLeft = topLeft,
                        size = Size(size, size),
                        cornerRadius = CornerRadius(state.currentCornerRadius, state.currentCornerRadius),
                        style = Stroke(width = state.currentStrokeWidth)
                    )
                }
                Tool.ELLIPSE -> {
                    drawOval(
                        color = state.currentColor,
                        topLeft = topLeft,
                        size = Size(width, height),
                        style = Stroke(width = state.currentStrokeWidth)
                    )
                }
                else -> {}
            }
        }

        if (state.bezierNodes.isNotEmpty()) {
            state.bezierNodes.forEach { node ->
                drawCircle(
                    color = state.currentColor,
                    radius = 6f,
                    center = node
                )
            }
            if (state.bezierNodes.size >= 2) {
                drawBezierCurve(state.bezierNodes, state.currentColor, state.currentStrokeWidth)
            }
        }
    }
}

private fun DrawScope.drawElement(element: DrawingElement) {
    when (element) {
        is FreePath -> drawFreePath(element.points, element.color, element.strokeWidth)
        is BezierCurve -> drawBezierCurve(element.nodes, element.color, element.strokeWidth)
        is Square -> drawRect(
            color = element.color,
            topLeft = element.topLeft,
            size = Size(element.size, element.size),
            style = Stroke(width = element.strokeWidth)
        )
        is RoundedRect -> drawRoundRect(
            color = element.color,
            topLeft = element.topLeft,
            size = Size(element.size, element.size),
            cornerRadius = CornerRadius(element.cornerRadius, element.cornerRadius),
            style = Stroke(width = element.strokeWidth)
        )
        is Ellipse -> drawOval(
            color = element.color,
            topLeft = Offset(element.center.x - element.radiusX, element.center.y - element.radiusY),
            size = Size(element.radiusX * 2, element.radiusY * 2),
            style = Stroke(width = element.strokeWidth)
        )
        is FillArea -> drawPath(
            path = element.path,
            color = element.fillColor,
            style = Fill
        )
    }
}

private fun DrawScope.drawFreePath(points: List<Offset>, color: Color, strokeWidth: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawBezierCurve(nodes: List<Offset>, color: Color, strokeWidth: Float) {
    if (nodes.size < 2) return
    val path = Path().apply {
        moveTo(nodes[0].x, nodes[0].y)
        for (i in 1 until nodes.size) {
            val prev = nodes[i - 1]
            val curr = nodes[i]
            val cpx1 = (prev.x + curr.x) / 2
            val cpy1 = prev.y
            val cpx2 = (prev.x + curr.x) / 2
            val cpy2 = curr.y
            cubicTo(cpx1, cpy1, cpx2, cpy2, curr.x, curr.y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
