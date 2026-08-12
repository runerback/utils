package com.runerback.comfyuiapi.ui.gallery

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.comfyuiapi.data.model.GeneratedOutput
import com.runerback.comfyuiapi.data.model.OutputKind
import com.runerback.comfyuiapi.ui.MainViewModel

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
    var playingUri by remember { mutableStateOf<Uri?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    fun stopAudio() {
        if (player.isPlaying) player.stop()
        playingUri = null
    }

    fun playAudio(uri: Uri) {
        if (playingUri == uri && player.isPlaying) {
            player.pause()
            playingUri = null
            return
        }
        player.reset()
        try {
            player.setDataSource(context, uri)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { playingUri = null }
            player.prepareAsync()
            playingUri = uri
        } catch (_: Exception) {
            playingUri = null
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
        when (output.kind) {
            OutputKind.Image -> {
                output.bitmap?.let { bitmap ->
                    ImagePreviewDialog(
                        bitmap = bitmap,
                        onDismiss = { selectedOutput = null }
                    )
                }
            }
            OutputKind.Audio -> {
                output.audioUri?.let { uri ->
                    AudioPreviewDialog(
                        output = output,
                        isPlaying = playingUri == uri && player.isPlaying,
                        onPlayToggle = { playAudio(uri) },
                        onDismiss = {
                            stopAudio()
                            selectedOutput = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(
    output: GeneratedOutput,
    onClick: () -> Unit
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Audio output",
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .aspectRatio(1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = output.filename,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    bitmap: ImageBitmap,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview"
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioPreviewDialog(
    output: GeneratedOutput,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
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
                    text = output.filename,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onPlayToggle) {
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
