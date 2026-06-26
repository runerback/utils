package com.runerback.drawer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.drawer.ui.components.ColorPicker
import com.runerback.drawer.ui.components.DrawingCanvas
import com.runerback.drawer.ui.components.LayerPanel
import com.runerback.drawer.ui.components.LogsDialog
import com.runerback.drawer.ui.components.StrokeWidthSlider
import com.runerback.drawer.ui.components.ToolBar
import com.runerback.drawer.ui.viewmodel.DrawerViewModel
import com.runerback.drawer.ui.viewmodel.Tool

@Composable
fun DrawerScreen(viewModel: DrawerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var showLogs by remember { mutableStateOf(false) }

    val selectedLayer = state.layers.find { it.id == state.selectedLayerId }
    val canDraw = selectedLayer?.isLocked == false

    Scaffold(
        topBar = {
            ToolBar(
                currentTool = state.currentTool,
                onToolSelected = { viewModel.setTool(it) },
                canUndo = state.undoStack.isNotEmpty(),
                canRedo = state.redoStack.isNotEmpty(),
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onClear = { viewModel.clear() },
                onShowLogs = { showLogs = true },
                bezierMode = state.isPlacingBezier,
                onBezierFinish = { viewModel.finishBezier() },
                onBezierCancel = { viewModel.cancelBezier() }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                ColorPicker(
                    currentColor = state.currentColor,
                    onColorSelected = { viewModel.setColor(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                StrokeWidthSlider(
                    currentWidth = state.currentStrokeWidth,
                    onWidthChanged = { viewModel.setStrokeWidth(it) }
                )
                if (state.currentTool == Tool.ROUNDED_RECT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StrokeWidthSlider(
                        label = "Corner Radius",
                        currentWidth = state.currentCornerRadius,
                        onWidthChanged = { viewModel.setCornerRadius(it) },
                        valueRange = 0f..100f
                    )
                }
            }
        }
    ) { padding ->
        if (showLogs) {
            LogsDialog(onDismiss = { showLogs = false })
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            DrawingCanvas(
                state = state,
                canDrawOnSelectedLayer = canDraw,
                onPenDrag = { viewModel.addPathPoint(it) },
                onPenDragEnd = { viewModel.finishPath() },
                onShapeStart = { viewModel.startShape(it) },
                onShapeMove = { viewModel.updateShapeEnd(it) },
                onShapeEnd = { viewModel.finishShape() },
                onBezierTap = { viewModel.addBezierNode(it) },
                onFillTap = { viewModel.fillAt(it) }
            )

            LayerPanel(
                layers = state.layers,
                selectedLayerId = state.selectedLayerId,
                onSelectLayer = { viewModel.selectLayer(it) },
                onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                onToggleLock = { viewModel.toggleLayerLock(it) },
                onCreateLayer = { viewModel.createLayer() },
                onDeleteLayer = { viewModel.deleteLayer(it) },
                onMergeDown = { layerId ->
                    val index = state.layers.indexOfFirst { it.id == layerId }
                    if (index in 0 until state.layers.lastIndex) {
                        viewModel.mergeSelectedLayers(
                            targetLayerId = state.layers[index + 1].id,
                            mergeLayerId = layerId
                        )
                    }
                },
                onMoveUp = { viewModel.moveLayerUp(it) },
                onMoveDown = { viewModel.moveLayerDown(it) },
                onRenameLayer = { id, name -> viewModel.renameLayer(id, name) },
                onShowLogs = { showLogs = true }
            )
        }
    }
}
