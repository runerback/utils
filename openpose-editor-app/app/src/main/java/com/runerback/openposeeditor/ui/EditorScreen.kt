package com.runerback.openposeeditor.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.openposeeditor.render.EditorGLRenderer
import com.runerback.openposeeditor.skeleton.KeypointGroup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

private const val MENU_ICON_BUTTON_WIDTH_DP = 48

@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val (glSurfaceView, renderer) = remember { createGLSurfaceView(context, viewModel) }

    var showPreview by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewJson by remember { mutableStateOf("") }
    var showLogView by remember { mutableStateOf(false) }

    val json = remember { Json { prettyPrint = true } }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { saveProject(context, it, viewModel, json) }
    }

    val loadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { loadProject(context, it, viewModel, json) }
    }

    DisposableEffect(glSurfaceView) {
        onDispose {
            glSurfaceView.queueEvent {
                renderer.release()
            }
        }
    }

    val selectedCategory = viewModel.editorState.selectedMenuCategory.value

    val onPoseModeChanged = remember(renderer) { { enabled: Boolean ->
        viewModel.setPoseModeEnabled(enabled)
        if (enabled) {
            viewModel.closeMenu()
        } else {
            viewModel.selectJoint(null)
            renderer.selectedJointId = null
        }
    } }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { glSurfaceView },
            modifier = Modifier.fillMaxSize(),
        )

        WorkzoneToolbar(
            poseModeEnabled = viewModel.editorState.poseModeEnabled.value,
            onPoseModeClick = { onPoseModeChanged(!viewModel.editorState.poseModeEnabled.value) },
        )

        if (selectedCategory != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { viewModel.closeMenu() }
                    },
            )
        }

        SideMenu(
            viewModel = viewModel,
            onShowOption = { group ->
                viewModel.toggleGroup(group)
            },
            onZoomOption = { group ->
                viewModel.frameGroup(group)
                viewModel.closeMenu()
            },
            onReturn = {
                viewModel.returnToPreviousCamera()
                viewModel.closeMenu()
            },
            onPoseModeChanged = onPoseModeChanged,
            onLockCameraViewChanged = { locked ->
                viewModel.setLockCameraView(locked)
            },
            onSyncCameraToViewport = {
                viewModel.syncRenderCameraToViewport()
                viewModel.closeMenu()
            },
            onResetViewport = {
                viewModel.resetViewportCamera()
                viewModel.closeMenu()
            },
            onPreview = {
                exportPreview(context, glSurfaceView, renderer) { bitmap, jsonStr ->
                    previewBitmap = bitmap
                    previewJson = jsonStr
                    showPreview = true
                }
                viewModel.closeMenu()
            },
            onSaveProject = {
                saveLauncher.launch("openpose_project.json")
                viewModel.closeMenu()
            },
            onLoadProject = {
                loadLauncher.launch(arrayOf("application/json"))
                viewModel.closeMenu()
            },
            onShowLogs = {
                showLogView = true
                viewModel.closeMenu()
            },
        )
    }

    if (showLogView) {
        LogViewDialog(
            onDismiss = { showLogView = false },
        )
    }

    if (showPreview) {
        PreviewDialog(
            bitmap = previewBitmap,
            json = previewJson,
            onExportPng = { bitmap ->
                savePng(context, bitmap)
            },
            onExportJson = { json ->
                saveJson(context, json)
            },
            onDismiss = {
                showPreview = false
                previewBitmap = null
                previewJson = ""
            },
        )
    }
}

@Composable
private fun SideMenu(
    viewModel: EditorViewModel,
    onShowOption: (KeypointGroup) -> Unit,
    onZoomOption: (KeypointGroup?) -> Unit,
    onReturn: () -> Unit,
    onPoseModeChanged: (Boolean) -> Unit,
    onLockCameraViewChanged: (Boolean) -> Unit,
    onSyncCameraToViewport: () -> Unit,
    onResetViewport: () -> Unit,
    onPreview: () -> Unit,
    onSaveProject: () -> Unit,
    onLoadProject: () -> Unit,
    onShowLogs: () -> Unit,
) {
    val selectedCategory = viewModel.editorState.selectedMenuCategory.value

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryButton(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Show",
                selected = selectedCategory == MenuCategory.SHOW,
            ) { viewModel.toggleMenuCategory(MenuCategory.SHOW) }
            CategoryButton(
                imageVector = Icons.Default.Accessibility,
                contentDescription = "Pose",
                selected = selectedCategory == MenuCategory.POSE,
            ) { viewModel.toggleMenuCategory(MenuCategory.POSE) }
            CategoryButton(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = "Zoom",
                selected = selectedCategory == MenuCategory.ZOOM,
            ) { viewModel.toggleMenuCategory(MenuCategory.ZOOM) }
            CategoryButton(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Camera",
                selected = selectedCategory == MenuCategory.CAMERA,
            ) { viewModel.toggleMenuCategory(MenuCategory.CAMERA) }
            CategoryButton(
                imageVector = Icons.Default.Folder,
                contentDescription = "File",
                selected = selectedCategory == MenuCategory.FILE,
            ) { viewModel.toggleMenuCategory(MenuCategory.FILE) }
            CategoryButton(
                imageVector = Icons.Default.Settings,
                contentDescription = "System",
                selected = selectedCategory == MenuCategory.SYSTEM,
            ) { viewModel.toggleMenuCategory(MenuCategory.SYSTEM) }
        }

        if (selectedCategory != null) {
            SubMenu(
                category = selectedCategory,
                viewModel = viewModel,
                onShowOption = onShowOption,
                onZoomOption = onZoomOption,
                onReturn = onReturn,
                onPoseModeChanged = onPoseModeChanged,
                onLockCameraViewChanged = onLockCameraViewChanged,
                onSyncCameraToViewport = onSyncCameraToViewport,
                onResetViewport = onResetViewport,
                onPreview = onPreview,
                onSaveProject = onSaveProject,
                onLoadProject = onLoadProject,
                onShowLogs = onShowLogs,
            )
        }
    }
}

@Composable
private fun SubMenu(
    category: MenuCategory,
    viewModel: EditorViewModel,
    onShowOption: (KeypointGroup) -> Unit,
    onZoomOption: (KeypointGroup?) -> Unit,
    onReturn: () -> Unit,
    onPoseModeChanged: (Boolean) -> Unit,
    onLockCameraViewChanged: (Boolean) -> Unit,
    onSyncCameraToViewport: () -> Unit,
    onResetViewport: () -> Unit,
    onPreview: () -> Unit,
    onSaveProject: () -> Unit,
    onLoadProject: () -> Unit,
    onShowLogs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(start = 8.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (category) {
            MenuCategory.SHOW -> {
                ShowCheckbox("Left Hand", KeypointGroup.LEFT_HAND, viewModel, onShowOption)
                ShowCheckbox("Right Hand", KeypointGroup.RIGHT_HAND, viewModel, onShowOption)
                ShowCheckbox("Left Foot", KeypointGroup.LEFT_FOOT, viewModel, onShowOption)
                ShowCheckbox("Right Foot", KeypointGroup.RIGHT_FOOT, viewModel, onShowOption)
                ShowCheckbox("Face", KeypointGroup.FACE, viewModel, onShowOption)
            }
            MenuCategory.POSE -> {
                PoseModeCheckbox(viewModel, onPoseModeChanged)
            }
            MenuCategory.ZOOM -> {
                SubButton("Full Body") { onZoomOption(null) }
                SubButton("L Hand") { onZoomOption(KeypointGroup.LEFT_HAND) }
                SubButton("R Hand") { onZoomOption(KeypointGroup.RIGHT_HAND) }
                SubButton("L Foot") { onZoomOption(KeypointGroup.LEFT_FOOT) }
                SubButton("R Foot") { onZoomOption(KeypointGroup.RIGHT_FOOT) }
                SubButton("Face") { onZoomOption(KeypointGroup.FACE) }
                SubButton("Return", enabled = viewModel.savedCameraState.value != null) { onReturn() }
            }
            MenuCategory.CAMERA -> {
                CameraLockCheckbox(viewModel, onLockCameraViewChanged)
                SubButton("Sync to viewport") { onSyncCameraToViewport() }
                SubButton("Reset viewport") { onResetViewport() }
            }
            MenuCategory.FILE -> {
                SubButton("Preview") { onPreview() }
                SubButton("Save") { onSaveProject() }
                SubButton("Load") { onLoadProject() }
            }
            MenuCategory.SYSTEM -> {
                SubButton("Show Logs") { onShowLogs() }
            }
        }
    }
}

@Composable
private fun WorkzoneToolbar(
    poseModeEnabled: Boolean,
    onPoseModeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            onClick = onPoseModeClick,
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Accessibility,
                    contentDescription = if (poseModeEnabled) "Exit pose mode" else "Enter pose mode",
                    tint = if (poseModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
                )
                Text(
                    text = if (poseModeEnabled) "Pose" else "View",
                    color = if (poseModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryButton(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.width(MENU_ICON_BUTTON_WIDTH_DP.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        )
    }
}

@Composable
private fun SubButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(140.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun PoseModeCheckbox(
    viewModel: EditorViewModel,
    onPoseModeChanged: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(140.dp)
            .clickable { onPoseModeChanged(!viewModel.editorState.poseModeEnabled.value) },
    ) {
        Checkbox(
            checked = viewModel.editorState.poseModeEnabled.value,
            onCheckedChange = { onPoseModeChanged(it) },
        )
        Text("Pose mode", color = Color.White)
    }
}

@Composable
private fun CameraLockCheckbox(
    viewModel: EditorViewModel,
    onLockChanged: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(140.dp)
            .clickable { onLockChanged(!viewModel.editorState.lockCameraView.value) },
    ) {
        Checkbox(
            checked = viewModel.editorState.lockCameraView.value,
            onCheckedChange = { onLockChanged(it) },
        )
        Text("Lock view", color = Color.White)
    }
}

@Composable
private fun ShowCheckbox(
    label: String,
    group: KeypointGroup,
    viewModel: EditorViewModel,
    onToggle: (KeypointGroup) -> Unit,
) {
    val checked = viewModel.editorState.groupVisibility[group] ?: true
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(140.dp)
            .clickable { onToggle(group) },
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle(group) },
        )
        Text(label, color = Color.White)
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun createGLSurfaceView(context: Context, viewModel: EditorViewModel): Pair<GLSurfaceView, EditorGLRenderer> {
    val renderer = EditorGLRenderer(context.assets)
    val view = GLSurfaceView(context).apply {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        renderer.skeleton = viewModel.skeleton
        renderer.viewportCamera = viewModel.viewportCamera
        renderer.renderCamera = viewModel.renderCamera

        setOnTouchListener { _, event ->
            handleTouch(event, viewModel, renderer, this)
            true
        }
    }
    return view to renderer
}

private var lastX = 0f
private var lastY = 0f
private var dragStartX = 0f
private var dragStartY = 0f
private var pinchStartDistance = 0f
private var isPinching = false

private fun handleTouch(
    event: MotionEvent,
    viewModel: EditorViewModel,
    renderer: EditorGLRenderer,
    glSurfaceView: GLSurfaceView,
) {
    val width = glSurfaceView.width
    val height = glSurfaceView.height
    val poseMode = viewModel.editorState.poseModeEnabled.value
    val message = "handleTouch action=${event.actionMasked} poseMode=$poseMode size=${width}x$height"
    Log.d("OpenPoseEditor", message)
    LogBuffer.add(message)
    if (poseMode) {
        handlePoseTouch(event, viewModel, renderer, glSurfaceView, width, height)
    } else {
        handleViewportTouch(event, viewModel, glSurfaceView)
    }
}

private fun handleViewportTouch(event: MotionEvent, viewModel: EditorViewModel, glSurfaceView: GLSurfaceView) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            lastX = event.x
            lastY = event.y
            isPinching = false
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
            if (event.pointerCount == 2) {
                isPinching = true
                pinchStartDistance = pinchDistance(event)
            }
        }
        MotionEvent.ACTION_MOVE -> {
            if (isPinching && event.pointerCount == 2) {
                val distance = pinchDistance(event)
                val scale = pinchStartDistance / distance
                viewModel.viewportDistance.floatValue *= scale.coerceIn(0.8f, 1.25f)
                viewModel.updateViewportCamera()
                if (viewModel.editorState.lockCameraView.value) {
                    viewModel.syncRenderCameraToViewport()
                }
                pinchStartDistance = distance
            } else {
                val dx = event.x - lastX
                val dy = event.y - lastY
                viewModel.viewportAzimuth.floatValue -= dx * 0.5f
                viewModel.viewportElevation.floatValue += dy * 0.5f
                viewModel.viewportElevation.floatValue = viewModel.viewportElevation.floatValue.coerceIn(-80f, 80f)
                viewModel.updateViewportCamera()
                if (viewModel.editorState.lockCameraView.value) {
                    viewModel.syncRenderCameraToViewport()
                }
            }
            lastX = event.x
            lastY = event.y
            glSurfaceView.requestRender()
        }
        MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
            if (event.pointerCount <= 2) isPinching = false
        }
    }
}

private fun handlePoseTouch(
    event: MotionEvent,
    viewModel: EditorViewModel,
    renderer: EditorGLRenderer,
    glSurfaceView: GLSurfaceView,
    width: Int,
    height: Int,
) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            val jointId = pickJoint(event.x, event.y, viewModel, width, height)
            val message = "pickJoint at (${event.x}, ${event.y}) -> $jointId"
            Log.d("OpenPoseEditor", message)
            LogBuffer.add(message)
            viewModel.selectJoint(jointId)
            renderer.selectedJointId = jointId
            dragStartX = event.x
            dragStartY = event.y
            lastX = event.x
            lastY = event.y
            if (jointId != null) {
                viewModel.beginJointDrag(event.x, event.y, width, height)
            }
            glSurfaceView.requestRender()
        }
        MotionEvent.ACTION_MOVE -> {
            val jointId = viewModel.editorState.selectedJointId.value
            if (jointId != null && event.pointerCount == 1) {
                val totalDx = event.x - dragStartX
                val totalDy = event.y - dragStartY
                if (totalDx != 0f || totalDy != 0f) {
                    viewModel.moveSelectedJointByScreenDelta(totalDx, totalDy, width, height)
                    glSurfaceView.requestRender()
                }
            }
            lastX = event.x
            lastY = event.y
        }
        MotionEvent.ACTION_UP -> {
            // no-op; selection stays active until user taps empty space or another joint
        }
    }
}

private fun pickJoint(screenX: Float, screenY: Float, viewModel: EditorViewModel, width: Int, height: Int): Int? {
    val skeleton = viewModel.skeleton
    val positions = skeleton.computeWorldPositions()
    val thresholdSq = 30f * 30f
    var bestId: Int? = null
    var bestDistSq = Float.MAX_VALUE
    for (kp in skeleton.keypoints) {
        if (!kp.enabled) continue
        val pos = positions[kp.id] ?: continue
        val (sx, sy) = viewModel.viewportCamera.project(pos, width, height)
        val dx = screenX - sx
        val dy = screenY - sy
        val distSq = dx * dx + dy * dy
        Log.v("OpenPoseEditor", "joint ${kp.name} id=${kp.id} enabled=${kp.enabled} screen=($sx, $sy) distSq=$distSq")
        LogBuffer.add("joint ${kp.name} id=${kp.id} screen=($sx, $sy) distSq=$distSq")
        if (distSq < thresholdSq && distSq < bestDistSq) {
            bestDistSq = distSq
            bestId = kp.id
        }
    }
    val summary = "pickJoint best=$bestId bestDistSq=$bestDistSq thresholdSq=$thresholdSq"
    Log.d("OpenPoseEditor", summary)
    LogBuffer.add(summary)
    return bestId
}

private fun pinchDistance(event: MotionEvent): Float {
    val dx = event.getX(0) - event.getX(1)
    val dy = event.getY(0) - event.getY(1)
    return hypot(dx, dy)
}

private fun exportPreview(
    context: Context,
    glSurfaceView: GLSurfaceView,
    renderer: EditorGLRenderer,
    onResult: (Bitmap, String) -> Unit,
) {
    val width = 512
    val height = 512
    renderer.export(glSurfaceView, width, height) { bitmap, json ->
        onResult(bitmap, json)
    }
}

private fun savePng(context: Context, bitmap: Bitmap) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.getExternalFilesDir(null), "OpenPoseEditor").apply { mkdirs() }
        val imageFile = File(dir, "pose_$timestamp.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Toast.makeText(context, "PNG exported to ${imageFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "PNG export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun saveJson(context: Context, json: String) {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.getExternalFilesDir(null), "OpenPoseEditor").apply { mkdirs() }
        val jsonFile = File(dir, "pose_$timestamp.json")
        jsonFile.writeText(json)
        Toast.makeText(context, "JSON exported to ${jsonFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "JSON export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun saveProject(context: Context, uri: Uri, viewModel: EditorViewModel, json: Json) {
    try {
        val state = viewModel.captureProjectState()
        val text = json.encodeToString(state)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        Toast.makeText(context, "Project saved", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun loadProject(context: Context, uri: Uri, viewModel: EditorViewModel, json: Json) {
    try {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return
        val state = json.decodeFromString<ProjectState>(text)
        viewModel.applyProjectState(state)
        Toast.makeText(context, "Project loaded", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
