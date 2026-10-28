package com.runerback.imagehelper.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.imagehelper.ui.components.FrameStrip
import com.runerback.imagehelper.ui.components.GifWorkzone
import com.runerback.imagehelper.ui.viewmodel.GifMakerState
import com.runerback.imagehelper.ui.viewmodel.GifMakerViewModel

@Composable
fun GifMakerScreen(viewModel: GifMakerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeExportMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        val width = state.gifWidth
        val height = state.gifHeight
        if (width == null || height == null) {
            GifSizeSetup(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onStart = { w, h -> viewModel.setSize(w, h) }
            )
        } else {
            GifWorkArea(
                state = state,
                gifWidth = width,
                gifHeight = height,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun GifSizeSetup(
    onStart: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthText by remember { mutableStateOf("480") }
    var heightText by remember { mutableStateOf("480") }
    val width = widthText.toIntOrNull()
    val height = heightText.toIntOrNull()
    val valid = width != null && height != null && width >= 16 && height >= 16 &&
        width <= 4096 && height <= 4096

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GIF size",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = widthText,
                onValueChange = { widthText = it.filter(Char::isDigit) },
                label = { Text("Width") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Text(text = "×", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it.filter(Char::isDigit) },
                label = { Text("Height") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(480 to 480, 512 to 384, 640 to 360).forEach { (w, h) ->
                TextButton(onClick = {
                    widthText = w.toString()
                    heightText = h.toString()
                }) {
                    Text("$w×$h")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            enabled = valid,
            onClick = { onStart(width!!, height!!) }
        ) {
            Text("Start")
        }
    }
}

@Composable
private fun GifWorkArea(
    state: GifMakerState,
    gifWidth: Int,
    gifHeight: Int,
    viewModel: GifMakerViewModel,
    modifier: Modifier = Modifier
) {
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addFrames(uris)
    }
    val launchPicker = {
        pickImages.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val context = LocalContext.current
    val canvasBackground = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val exportGif = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/gif")
    ) { uri ->
        if (uri != null) viewModel.exportGif(context, uri, canvasBackground)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$gifWidth×$gifHeight · ${state.frames.size} frames",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { launchPicker() }) {
                Text("Add images")
            }
            TextButton(
                enabled = state.frames.isNotEmpty() && !state.exporting,
                onClick = {
                    exportGif.launch("gif-maker-${System.currentTimeMillis()}.gif")
                }
            ) {
                Text("Export")
            }
        }

        if (state.exporting) {
            LinearProgressIndicator(
                progress = state.exportProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            GifWorkzone(
                frames = state.frames,
                selectedFrameIndex = state.selectedFrameIndex,
                gifWidth = gifWidth,
                gifHeight = gifHeight,
                editMode = state.editMode,
                onToggleEditMode = { viewModel.setEditMode(it) },
                onTransformChange = { index, transform ->
                    viewModel.updateFrameTransform(index, transform)
                },
                onFitScaleChange = { viewModel.setCanvasFitScale(it) },
                modifier = Modifier.fillMaxSize()
            )

            if (state.editMode && state.selectedFrameIndex != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Drag to move · Pinch to zoom",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            state.selectedFrameIndex?.let { viewModel.resetFrameTransform(it) }
                        }) {
                            Text("Reset")
                        }
                        TextButton(onClick = { viewModel.setEditMode(false) }) {
                            Text("Done")
                        }
                    }
                }
            }
        }

        FrameStrip(
            frames = state.frames,
            selectedIndex = state.selectedFrameIndex,
            onSelect = { viewModel.selectFrame(it) },
            onAddImages = { launchPicker() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
