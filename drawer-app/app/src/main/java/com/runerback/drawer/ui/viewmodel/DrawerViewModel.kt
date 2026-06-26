package com.runerback.drawer.ui.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.lifecycle.ViewModel
import com.runerback.drawer.model.BezierCurve
import com.runerback.drawer.model.DrawingAction
import com.runerback.drawer.model.DrawingElement
import com.runerback.drawer.model.Ellipse
import com.runerback.drawer.model.FillArea
import com.runerback.drawer.model.FreePath
import com.runerback.drawer.model.Layer
import com.runerback.drawer.model.RoundedRect
import com.runerback.drawer.model.Square
import com.runerback.drawer.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Tool {
    PEN, BEZIER, SQUARE, ROUNDED_RECT, ELLIPSE, FILL
}

data class DrawerState(
    val layers: List<Layer> = listOf(Layer(id = uuid(), name = "Layer 1")),
    val selectedLayerId: String = "",
    val elements: Map<String, DrawingElement> = emptyMap(),
    val currentTool: Tool = Tool.PEN,
    val currentColor: Color = Color.White,
    val currentStrokeWidth: Float = 4f,
    val currentCornerRadius: Float = 16f,
    val inProgressPath: List<Offset> = emptyList(),
    val inProgressShapeStart: Offset? = null,
    val inProgressShapeEnd: Offset? = null,
    val bezierNodes: List<Offset> = emptyList(),
    val isPlacingBezier: Boolean = false,
    val undoStack: List<DrawingAction> = emptyList(),
    val redoStack: List<DrawingAction> = emptyList()
)

private fun uuid(): String = UUID.randomUUID().toString()

class DrawerViewModel : ViewModel() {

    companion object {
        private const val TAG = "DrawerViewModel"
    }

    private val _state = MutableStateFlow(DrawerState())
    val state: StateFlow<DrawerState> = _state.asStateFlow()

    init {
        LogManager.d(TAG, "init")
        _state.update { it.copy(selectedLayerId = it.layers.first().id) }
    }

    private fun pushAction(action: DrawingAction) {
        _state.update {
            it.copy(undoStack = it.undoStack + action, redoStack = emptyList())
        }
    }

    private fun selectedLayer(): Layer? {
        val layer = _state.value.layers.find { it.id == _state.value.selectedLayerId }
        if (layer == null) {
            LogManager.w(TAG, "selectedLayer() returned null for id=${_state.value.selectedLayerId}")
        }
        return layer
    }

    private fun requireSelectedLayer(): Layer {
        return selectedLayer() ?: throw IllegalStateException(
            "No selected layer (selectedLayerId=${_state.value.selectedLayerId})"
        )
    }

    fun setTool(tool: Tool) {
        try {
            _state.update {
                it.copy(
                    currentTool = tool,
                    bezierNodes = emptyList(),
                    isPlacingBezier = false,
                    inProgressPath = emptyList(),
                    inProgressShapeStart = null,
                    inProgressShapeEnd = null
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "setTool failed", e)
        }
    }

    fun setColor(color: Color) {
        _state.update { it.copy(currentColor = color) }
    }

    fun setStrokeWidth(width: Float) {
        _state.update { it.copy(currentStrokeWidth = width) }
    }

    fun setCornerRadius(radius: Float) {
        _state.update { it.copy(currentCornerRadius = radius) }
    }

    fun addPathPoint(point: Offset) {
        _state.update { it.copy(inProgressPath = it.inProgressPath + point) }
    }

    fun finishPath() {
        try {
            val layer = requireSelectedLayer()
            val stateSnapshot = _state.value
            if (stateSnapshot.inProgressPath.isEmpty()) return
            val element = FreePath(
                id = uuid(),
                points = stateSnapshot.inProgressPath,
                color = stateSnapshot.currentColor,
                strokeWidth = stateSnapshot.currentStrokeWidth
            )
            pushAction(DrawingAction.AddElement(element, layer.id))
            _state.update { state ->
                state.copy(
                    elements = state.elements + (element.id to element),
                    layers = state.layers.map { l ->
                        if (l.id == state.selectedLayerId) {
                            l.copy(elementIds = l.elementIds + element.id)
                        } else l
                    },
                    inProgressPath = emptyList()
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "finishPath failed", e)
        }
    }

    fun startShape(start: Offset) {
        _state.update { it.copy(inProgressShapeStart = start, inProgressShapeEnd = start) }
    }

    fun updateShapeEnd(end: Offset) {
        _state.update { it.copy(inProgressShapeEnd = end) }
    }

    fun finishShape() {
        try {
            val layer = requireSelectedLayer()
            val stateSnapshot = _state.value
            val start = stateSnapshot.inProgressShapeStart ?: return
            val end = stateSnapshot.inProgressShapeEnd ?: return
            val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
            val width = abs(end.x - start.x)
            val height = abs(end.y - start.y)

            val element: DrawingElement = when (stateSnapshot.currentTool) {
                Tool.SQUARE -> {
                    val size = max(width, height)
                    Square(
                        id = uuid(),
                        topLeft = topLeft,
                        size = size,
                        color = stateSnapshot.currentColor,
                        strokeWidth = stateSnapshot.currentStrokeWidth
                    )
                }
                Tool.ROUNDED_RECT -> {
                    val size = max(width, height)
                    RoundedRect(
                        id = uuid(),
                        topLeft = topLeft,
                        size = size,
                        cornerRadius = stateSnapshot.currentCornerRadius,
                        color = stateSnapshot.currentColor,
                        strokeWidth = stateSnapshot.currentStrokeWidth
                    )
                }
                Tool.ELLIPSE -> Ellipse(
                    id = uuid(),
                    center = Offset((start.x + end.x) / 2, (start.y + end.y) / 2),
                    radiusX = width / 2,
                    radiusY = height / 2,
                    color = stateSnapshot.currentColor,
                    strokeWidth = stateSnapshot.currentStrokeWidth
                )
                else -> {
                    LogManager.w(TAG, "finishShape called with wrong tool=${stateSnapshot.currentTool}")
                    return
                }
            }

            pushAction(DrawingAction.AddElement(element, layer.id))
            _state.update { state ->
                state.copy(
                    elements = state.elements + (element.id to element),
                    layers = state.layers.map { l ->
                        if (l.id == state.selectedLayerId) {
                            l.copy(elementIds = l.elementIds + element.id)
                        } else l
                    },
                    inProgressShapeStart = null,
                    inProgressShapeEnd = null
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "finishShape failed", e)
        }
    }

    fun addBezierNode(point: Offset) {
        _state.update {
            it.copy(
                bezierNodes = it.bezierNodes + point,
                isPlacingBezier = true
            )
        }
    }

    fun finishBezier() {
        try {
            val layer = requireSelectedLayer()
            val stateSnapshot = _state.value
            if (stateSnapshot.bezierNodes.size < 2) return
            val element = BezierCurve(
                id = uuid(),
                nodes = stateSnapshot.bezierNodes,
                color = stateSnapshot.currentColor,
                strokeWidth = stateSnapshot.currentStrokeWidth
            )
            pushAction(DrawingAction.AddElement(element, layer.id))
            _state.update { state ->
                state.copy(
                    elements = state.elements + (element.id to element),
                    layers = state.layers.map { l ->
                        if (l.id == state.selectedLayerId) {
                            l.copy(elementIds = l.elementIds + element.id)
                        } else l
                    },
                    bezierNodes = emptyList(),
                    isPlacingBezier = false
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "finishBezier failed", e)
        }
    }

    fun cancelBezier() {
        _state.update { it.copy(bezierNodes = emptyList(), isPlacingBezier = false) }
    }

    fun fillAt(point: Offset) {
        try {
            val layer = requireSelectedLayer()
            val stateSnapshot = _state.value
            val filledElement = stateSnapshot.elements.values.find { element ->
                when (element) {
                    is Square -> pointInRect(point, element.topLeft, element.size)
                    is RoundedRect -> pointInRect(point, element.topLeft, element.size)
                    is Ellipse -> pointInEllipse(point, element.center, element.radiusX, element.radiusY)
                    else -> false
                }
            }

            if (filledElement == null) {
                LogManager.d(TAG, "fillAt: no shape found at $point")
                return
            }

            val fillArea = when (filledElement) {
                is Square -> FillArea(
                    id = uuid(),
                    path = Path().apply {
                        addRect(
                            Rect(
                                filledElement.topLeft,
                                Size(filledElement.size, filledElement.size)
                            )
                        )
                    },
                    fillColor = stateSnapshot.currentColor
                )
                is RoundedRect -> FillArea(
                    id = uuid(),
                    path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                Rect(
                                    filledElement.topLeft,
                                    Size(filledElement.size, filledElement.size)
                                ),
                                filledElement.cornerRadius,
                                filledElement.cornerRadius
                            )
                        )
                    },
                    fillColor = stateSnapshot.currentColor
                )
                is Ellipse -> FillArea(
                    id = uuid(),
                    path = Path().apply {
                        addOval(
                            Rect(
                                filledElement.center.x - filledElement.radiusX,
                                filledElement.center.y - filledElement.radiusY,
                                filledElement.center.x + filledElement.radiusX,
                                filledElement.center.y + filledElement.radiusY
                            )
                        )
                    },
                    fillColor = stateSnapshot.currentColor
                )
                else -> {
                    LogManager.w(TAG, "fillAt: unsupported element type ${filledElement::class.simpleName}")
                    return
                }
            }

            pushAction(DrawingAction.AddElement(fillArea, layer.id))
            _state.update { state ->
                state.copy(
                    elements = state.elements + (fillArea.id to fillArea),
                    layers = state.layers.map { l ->
                        if (l.id == state.selectedLayerId) {
                            l.copy(elementIds = l.elementIds + fillArea.id)
                        } else l
                    }
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "fillAt failed", e)
        }
    }

    fun undo() {
        try {
            _state.update { state ->
                if (state.undoStack.isEmpty()) return@update state
                val action = state.undoStack.last()
                val newState = applyInverse(action, state)
                newState.copy(
                    undoStack = state.undoStack.dropLast(1),
                    redoStack = listOf(action) + state.redoStack
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "undo failed", e)
        }
    }

    fun redo() {
        try {
            _state.update { state ->
                if (state.redoStack.isEmpty()) return@update state
                val action = state.redoStack.first()
                val newState = applyAction(action, state)
                newState.copy(
                    undoStack = state.undoStack + action,
                    redoStack = state.redoStack.drop(1)
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "redo failed", e)
        }
    }

    private fun applyAction(action: DrawingAction, state: DrawerState): DrawerState = try {
        when (action) {
            is DrawingAction.AddElement -> state.copy(
                elements = state.elements + (action.element.id to action.element),
                layers = state.layers.map { l ->
                    if (l.id == action.layerId) l.copy(elementIds = l.elementIds + action.element.id) else l
                }
            )
            is DrawingAction.CreateLayer -> state.copy(
                layers = state.layers + action.layer,
                selectedLayerId = action.layer.id
            )
            is DrawingAction.DeleteLayer -> {
                val newElements = state.elements.toMutableMap()
                action.layer.elementIds.forEach { id ->
                    if (newElements.containsKey(id)) {
                        newElements.remove(id)
                    } else {
                        LogManager.w(TAG, "applyAction DeleteLayer: element $id missing from map")
                    }
                }
                state.copy(
                    layers = state.layers.filter { it.id != action.layer.id },
                    elements = newElements,
                    selectedLayerId = if (state.selectedLayerId == action.layer.id) {
                        state.layers.firstOrNull { it.id != action.layer.id }?.id ?: ""
                    } else state.selectedLayerId
                )
            }
            is DrawingAction.MergeLayers -> {
                val targetLayer = state.layers.find { it.id == action.targetLayerId }
                if (targetLayer == null) {
                    LogManager.w(TAG, "applyAction MergeLayers: target ${action.targetLayerId} missing")
                    state
                } else {
                    val removedLayers = state.layers.filter { it.id in action.removedLayerIds }
                    val mergedIds = removedLayers.flatMap { it.elementIds }
                    state.copy(
                        layers = state.layers.filter { it.id !in action.removedLayerIds }.map {
                            if (it.id == action.targetLayerId) it.copy(elementIds = it.elementIds + mergedIds) else it
                        },
                        selectedLayerId = action.targetLayerId
                    )
                }
            }
            is DrawingAction.MoveLayer -> {
                if (action.fromIndex !in state.layers.indices || action.toIndex !in state.layers.indices) {
                    LogManager.w(TAG, "applyAction MoveLayer: invalid indices ${action.fromIndex} -> ${action.toIndex}")
                    state
                } else {
                    val layers = state.layers.toMutableList()
                    val item = layers.removeAt(action.fromIndex)
                    layers.add(action.toIndex, item)
                    state.copy(layers = layers)
                }
            }
            is DrawingAction.Clear -> {
                val newLayer = Layer(id = uuid(), name = "Layer 1")
                state.copy(
                    layers = listOf(newLayer),
                    elements = emptyMap(),
                    selectedLayerId = newLayer.id
                )
            }
        }
    } catch (e: Exception) {
        LogManager.e(TAG, "applyAction failed for $action", e)
        state
    }

    private fun applyInverse(action: DrawingAction, state: DrawerState): DrawerState = try {
        when (action) {
            is DrawingAction.AddElement -> state.copy(
                elements = state.elements - action.element.id,
                layers = state.layers.map { l ->
                    if (l.id == action.layerId) l.copy(elementIds = l.elementIds - action.element.id) else l
                }
            )
            is DrawingAction.CreateLayer -> state.copy(
                layers = state.layers.filter { it.id != action.layer.id },
                selectedLayerId = state.layers.find { it.id != action.layer.id }?.id ?: ""
            )
            is DrawingAction.DeleteLayer -> state.copy(
                layers = insertLayer(state.layers, action.layer),
                elements = state.elements + action.elements
            )
            is DrawingAction.MergeLayers -> {
                val restoredLayers = action.previousElementIdsByLayer.mapNotNull { (layerId, ids) ->
                    state.layers.find { it.id == layerId }?.copy(elementIds = ids)
                }
                val missingIds = action.previousElementIdsByLayer.keys - state.layers.map { it.id }.toSet()
                if (missingIds.isNotEmpty()) {
                    LogManager.w(TAG, "applyInverse MergeLayers: missing layers $missingIds")
                }
                state.copy(
                    layers = state.layers.map { l ->
                        restoredLayers.find { it.id == l.id } ?: l
                    } + restoredLayers.filter { rl -> state.layers.none { it.id == rl.id } },
                    selectedLayerId = action.previousSelectedLayerId
                )
            }
            is DrawingAction.MoveLayer -> {
                if (action.fromIndex !in state.layers.indices || action.toIndex !in state.layers.indices) {
                    LogManager.w(TAG, "applyInverse MoveLayer: invalid indices ${action.fromIndex} -> ${action.toIndex}")
                    state
                } else {
                    val layers = state.layers.toMutableList()
                    val item = layers.removeAt(action.toIndex)
                    layers.add(action.fromIndex, item)
                    state.copy(layers = layers)
                }
            }
            is DrawingAction.Clear -> state.copy(
                layers = action.previousLayers,
                elements = action.previousElements,
                selectedLayerId = action.previousSelectedLayerId
            )
        }
    } catch (e: Exception) {
        LogManager.e(TAG, "applyInverse failed for $action", e)
        state
    }

    private fun insertLayer(layers: List<Layer>, layer: Layer): List<Layer> {
        val index = (layers.indexOfFirst { it.id > layer.id }).coerceAtLeast(0)
        return layers.toMutableList().apply { add(index, layer) }
    }

    fun createLayer() {
        try {
            val newLayer = Layer(id = uuid(), name = "Layer ${state.value.layers.size + 1}")
            val action = DrawingAction.CreateLayer(newLayer)
            _state.update { state ->
                val index = state.layers.indexOfFirst { it.id == state.selectedLayerId }
                val newLayers = state.layers.toMutableList()
                if (index in 0..newLayers.lastIndex) {
                    newLayers.add(index + 1, newLayer)
                } else {
                    newLayers.add(newLayer)
                }
                state.copy(
                    layers = newLayers,
                    selectedLayerId = newLayer.id,
                    undoStack = state.undoStack + action,
                    redoStack = emptyList()
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "createLayer failed", e)
        }
    }

    fun deleteLayer(layerId: String) {
        try {
            _state.update { state ->
                val layer = state.layers.find { it.id == layerId }
                if (layer == null) {
                    LogManager.w(TAG, "deleteLayer: layer $layerId not found")
                    return@update state
                }
                val removedElements = layer.elementIds.mapNotNull { id ->
                    state.elements[id]?.let { id to it } ?: run {
                        LogManager.w(TAG, "deleteLayer: element $id missing from map")
                        null
                    }
                }.toMap()
                pushAction(DrawingAction.DeleteLayer(layer, removedElements))
                val newLayers = state.layers.filter { it.id != layerId }
                val newElements = state.elements.toMutableMap()
                layer.elementIds.forEach { newElements.remove(it) }
                state.copy(
                    layers = newLayers,
                    elements = newElements,
                    selectedLayerId = if (state.selectedLayerId == layerId) {
                        newLayers.firstOrNull()?.id ?: ""
                    } else state.selectedLayerId
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "deleteLayer failed", e)
        }
    }

    fun selectLayer(layerId: String) {
        _state.update { it.copy(selectedLayerId = layerId) }
    }

    fun toggleLayerVisibility(layerId: String) {
        _state.update { state ->
            state.copy(
                layers = state.layers.map { l ->
                    if (l.id == layerId) l.copy(isVisible = !l.isVisible) else l
                }
            )
        }
    }

    fun toggleLayerLock(layerId: String) {
        _state.update { state ->
            state.copy(
                layers = state.layers.map { l ->
                    if (l.id == layerId) l.copy(isLocked = !l.isLocked) else l
                }
            )
        }
    }

    fun mergeSelectedLayers(targetLayerId: String, mergeLayerId: String) {
        try {
            _state.update { state ->
                val target = state.layers.find { it.id == targetLayerId }
                val merge = state.layers.find { it.id == mergeLayerId }
                if (target == null || merge == null) {
                    LogManager.w(TAG, "mergeSelectedLayers: target or merge missing")
                    return@update state
                }
                if (target.id == merge.id) return@update state

                val previousIds = mapOf(
                    target.id to target.elementIds,
                    merge.id to merge.elementIds
                )
                pushAction(
                    DrawingAction.MergeLayers(
                        removedLayerIds = listOf(merge.id),
                        targetLayerId = target.id,
                        previousElementIdsByLayer = previousIds,
                        previousSelectedLayerId = state.selectedLayerId
                    )
                )

                state.copy(
                    layers = state.layers.filter { it.id != merge.id }.map { l ->
                        if (l.id == target.id) l.copy(elementIds = target.elementIds + merge.elementIds) else l
                    },
                    selectedLayerId = target.id
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "mergeSelectedLayers failed", e)
        }
    }

    fun moveLayerUp(layerId: String) {
        try {
            _state.update { state ->
                val index = state.layers.indexOfFirst { it.id == layerId }
                if (index <= 0) return@update state
                pushAction(
                    DrawingAction.MoveLayer(
                        layerId = layerId,
                        fromIndex = index,
                        toIndex = index - 1
                    )
                )
                val layers = state.layers.toMutableList()
                val item = layers.removeAt(index)
                layers.add(index - 1, item)
                state.copy(layers = layers)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "moveLayerUp failed", e)
        }
    }

    fun moveLayerDown(layerId: String) {
        try {
            _state.update { state ->
                val index = state.layers.indexOfFirst { it.id == layerId }
                if (index < 0 || index >= state.layers.lastIndex) return@update state
                pushAction(
                    DrawingAction.MoveLayer(
                        layerId = layerId,
                        fromIndex = index,
                        toIndex = index + 1
                    )
                )
                val layers = state.layers.toMutableList()
                val item = layers.removeAt(index)
                layers.add(index + 1, item)
                state.copy(layers = layers)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "moveLayerDown failed", e)
        }
    }

    fun renameLayer(layerId: String, name: String) {
        _state.update { state ->
            state.copy(
                layers = state.layers.map { l ->
                    if (l.id == layerId) l.copy(name = name) else l
                }
            )
        }
    }

    fun clear() {
        try {
            _state.update { state ->
                pushAction(
                    DrawingAction.Clear(
                        previousLayers = state.layers,
                        previousElements = state.elements,
                        previousSelectedLayerId = state.selectedLayerId
                    )
                )
                val newLayer = Layer(id = uuid(), name = "Layer 1")
                state.copy(
                    layers = listOf(newLayer),
                    elements = emptyMap(),
                    selectedLayerId = newLayer.id
                )
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "clear failed", e)
        }
    }

    private fun pointInRect(point: Offset, topLeft: Offset, size: Float): Boolean {
        return point.x >= topLeft.x && point.x <= topLeft.x + size &&
            point.y >= topLeft.y && point.y <= topLeft.y + size
    }

    private fun pointInEllipse(point: Offset, center: Offset, radiusX: Float, radiusY: Float): Boolean {
        if (radiusX <= 0 || radiusY <= 0) return false
        val dx = point.x - center.x
        val dy = point.y - center.y
        return (dx * dx) / (radiusX * radiusX) + (dy * dy) / (radiusY * radiusY) <= 1f
    }
}
