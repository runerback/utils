package com.runerback.comfyuiapi.ui.gallery

import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.comfyuiapi.data.model.GeneratedOutput
import com.runerback.comfyuiapi.data.model.OutputKind
import com.runerback.comfyuiapi.ui.MainViewModel
import com.runerback.comfyuiapi.ui.components.LogBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputGalleryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val outputs by viewModel.allOutputs.collectAsStateWithLifecycle()
    val sortedOutputs = remember(outputs) { outputs.sortedByDescending { it.createdAt } }
    var selectedOutput by remember { mutableStateOf<GeneratedOutput?>(null) }

    val context = LocalContext.current
    val player = remember { MediaPlayer() }
    var activeUri by remember { mutableStateOf<Uri?>(null) }
    var isPreparing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    fun stopAudio() {
        if (player.isPlaying) player.stop()
        activeUri = null
        isPreparing = false
        isPlaying = false
    }

    fun moveToNext() {
        val current = selectedOutput ?: return
        val index = sortedOutputs.indexOf(current)
        if (index in 0 until sortedOutputs.lastIndex) {
            if (current.kind == OutputKind.Audio) stopAudio()
            selectedOutput = sortedOutputs[index + 1]
        }
    }

    fun moveToPrevious() {
        val current = selectedOutput ?: return
        val index = sortedOutputs.indexOf(current)
        if (index > 0) {
            if (current.kind == OutputKind.Audio) stopAudio()
            selectedOutput = sortedOutputs[index - 1]
        }
    }

    fun playAudio(uri: Uri) {
        if (activeUri == uri) {
            if (isPreparing) return
            if (isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
            return
        }
        player.reset()
        try {
            LogBuffer.add("gallery.playAudio: $uri")
            player.setDataSource(context, uri)
            player.setOnPreparedListener {
                LogBuffer.add("gallery.playAudio: prepared")
                it.start()
                isPreparing = false
                isPlaying = true
            }
            player.setOnCompletionListener { isPlaying = false }
            player.setOnErrorListener { _, what, extra ->
                LogBuffer.add("gallery.playAudio: error what=$what extra=$extra uri=$uri")
                activeUri = null
                isPreparing = false
                isPlaying = false
                true
            }
            player.prepareAsync()
            activeUri = uri
            isPreparing = true
            isPlaying = false
        } catch (e: Exception) {
            LogBuffer.add("gallery.playAudio: exception ${e.message}")
            activeUri = null
            isPreparing = false
            isPlaying = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (sortedOutputs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No outputs yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedOutputs, key = { "${it.createdAt}_${it.filename}" }) { output ->
                    GalleryThumbnail(
                        output = output,
                        onClick = { selectedOutput = output }
                    )
                }
            }
        }
    }

    selectedOutput?.let { output ->
        PreviewDialog(
            output = output,
            isPlaying = activeUri == output.audioUri && isPlaying,
            isLoading = activeUri == output.audioUri && isPreparing,
            onPlayToggle = { output.audioUri?.let { playAudio(it) } },
            onDownload = {
                viewModel.saveOutputToDownloads(output) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                stopAudio()
                selectedOutput = null
            },
            onSwipeLeft = ::moveToNext,
            onSwipeRight = ::moveToPrevious
        )
    }
}

@Composable
private fun GalleryThumbnail(
    output: GeneratedOutput,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (output.kind) {
            OutputKind.Image -> {
                val bitmap = output.bitmap ?: return
                val aspectRatio = remember(bitmap) {
                    val width = bitmap.width.coerceAtLeast(1)
                    val height = bitmap.height.coerceAtLeast(1)
                    width.toFloat() / height.toFloat()
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = "Generated image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clickable(onClick = onClick)
                )
            }
            OutputKind.Audio -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable(onClick = onClick)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Audio output",
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = output.filename,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PreviewDialog(
    output: GeneratedOutput,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayToggle: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            val constraintsMaxWidth = maxWidth
            val constraintsMaxHeight = maxHeight
            AnimatedContent(
                targetState = output,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(220))
                },
                label = "previewContent"
            ) { targetOutput ->
                when (targetOutput.kind) {
                    OutputKind.Image -> {
                        val bitmap = targetOutput.bitmap ?: return@AnimatedContent
                        val aspectRatio = remember(bitmap) {
                            val width = bitmap.width.coerceAtLeast(1)
                            val height = bitmap.height.coerceAtLeast(1)
                            width.toFloat() / height.toFloat()
                        }
                        val maxImageWidth = constraintsMaxWidth * 0.8f
                        val maxImageHeight = constraintsMaxHeight * 0.8f
                        val heightAtMaxWidth = maxImageWidth / aspectRatio
                        val (dialogWidth, dialogHeight) = if (heightAtMaxWidth > maxImageHeight) {
                            maxImageHeight * aspectRatio to maxImageHeight
                        } else {
                            maxImageWidth to heightAtMaxWidth
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RectangleShape,
                                modifier = Modifier
                                    .size(dialogWidth, dialogHeight)
                                    .swipeablePreview(
                                        onSwipeLeft = onSwipeLeft,
                                        onSwipeRight = onSwipeRight
                                    )
                            ) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = targetOutput.filename,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(dialogWidth)
                            )
                        }
                    }
                    OutputKind.Audio -> {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .swipeablePreview(
                                    onSwipeLeft = onSwipeLeft,
                                    onSwipeRight = onSwipeRight
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Audio output",
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = targetOutput.filename,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = onPlayToggle, enabled = !isLoading) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(48.dp),
                                            strokeWidth = 4.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            DownloadButton(
                onClick = onDownload,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

private fun Modifier.swipeablePreview(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier = pointerInput(onSwipeLeft, onSwipeRight) {
    val threshold = 50.dp.toPx()
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            totalDrag += dragAmount
        },
        onDragEnd = {
            when {
                totalDrag < -threshold -> onSwipeLeft()
                totalDrag > threshold -> onSwipeRight()
            }
            totalDrag = 0f
        }
    )
}

@Composable
private fun DownloadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
