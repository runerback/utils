package com.runerback.translator.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runerback.translator.ui.theme.TranslatorTheme
import com.runerback.translator.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The capture feature's own camera screen: live preview, shutter at the
 * bottom center, "Choose from album" at the bottom left, flashlight toggle at
 * the bottom right. Everything is black with white controls to match the
 * capture reader. A successful shot (or picked album image) is normalized by
 * [CapturedImageStore] and handed straight to [CaptureReaderActivity].
 */
class CameraCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setWindowAnimations(0)
        super.onCreate(savedInstanceState)
        setContent {
            TranslatorTheme {
                CameraCaptureScreen(
                    onImageReady = { scan ->
                        startActivity(CaptureReaderActivity.createIntent(this, scan))
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, CameraCaptureActivity::class.java)
        }
    }
}

@Composable
private fun CameraCaptureScreen(
    onImageReady: (File) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            hasPermission -> CameraPreviewContent(onImageReady = onImageReady)
            permissionDenied -> PermissionDeniedContent(
                onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera permission needed",
            color = Color.White,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Grant",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .border(1.dp, Color.White)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = "Close",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .border(1.dp, Color.White)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CameraPreviewContent(
    onImageReady: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            // Show the whole frame, consistent with the never-crop rule.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // After a successful capture/pick the normalized photo is reviewed first:
    // confirm opens the reader, retake deletes and returns to the preview.
    var reviewScan by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    imageCapture = capture
                }.onFailure {
                    LogManager.e("CameraCapture", "Camera bind failed", it)
                    bindError = true
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        LogManager.d("CameraCapture", "Album result uri=$uri")
        if (uri == null) {
            errorMessage = "Album returned no image"
            return@rememberLauncherForActivityResult
        }
        busy = true
        errorMessage = null
        scope.launch {
            val scan = CapturedImageStore.storeAlbumImage(context, uri)
            busy = false
            if (scan != null) {
                reviewScan = scan
            } else {
                errorMessage = "Cannot read this image (see Logs)"
            }
        }
    }

    fun capture() {
        val capture = imageCapture ?: return
        if (busy) return
        busy = true
        errorMessage = null
        LogManager.d("CameraCapture", "Shutter pressed, capturing")
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(CapturedImageStore.pendingFile(context)).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val scan = CapturedImageStore.storePendingCapture(context)
                        busy = false
                        if (scan != null) {
                            reviewScan = scan
                        } else {
                            errorMessage = "Cannot read capture (see Logs)"
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    LogManager.e("CameraCapture", "Capture failed", exception)
                    busy = false
                    errorMessage = "Capture failed (see Logs)"
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val scan = reviewScan
        if (scan != null) {
            CaptureReviewContent(
                scan = scan,
                onConfirm = { onImageReady(scan) },
                onRetake = {
                    scan.delete()
                    reviewScan = null
                },
            )
            return@Box
        }

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                // Pinch-to-zoom, clamped to the device's supported range.
                .pointerInput(camera) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        val state = cam.cameraInfo.zoomState.value
                            ?: return@detectTransformGestures
                        zoomRatio = (zoomRatio * zoom)
                            .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                        cam.cameraControl.setZoomRatio(zoomRatio)
                    }
                },
        )

        if (zoomRatio != 1f) {
            Text(
                text = "%.1fx".format(zoomRatio),
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        if (bindError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Camera unavailable",
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }

        // Visible failure feedback: the flow must never die silently.
        errorMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black)
                    .border(2.dp, Color.White)
                    .padding(16.dp),
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }

        // Bottom controls: album left, shutter center, flashlight right.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, Color.White, CircleShape)
                    .clickable(enabled = !busy) {
                        LogManager.d("CameraCapture", "Album button clicked, launching picker")
                        errorMessage = null
                        pickImage.launch("image/*")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = "Choose from album",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .clickable(enabled = !busy) { capture() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White, CircleShape),
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, Color.White, CircleShape)
                    .clickable(enabled = !busy) {
                        val cam = camera ?: return@clickable
                        if (!cam.cameraInfo.hasFlashUnit()) return@clickable
                        torchOn = !torchOn
                        cam.cameraControl.enableTorch(torchOn)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (torchOn) {
                        Icons.Outlined.FlashlightOn
                    } else {
                        Icons.Outlined.FlashlightOff
                    },
                    contentDescription = "Flashlight",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        if (busy) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/** Post-capture review: the normalized photo full-screen (exactly what the
 *  reader will show, border included), retake bottom-left, confirm
 *  bottom-right. The camera stays bound underneath so retake is instant. */
@Composable
private fun CaptureReviewContent(
    scan: File,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(scan) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(scan.absolutePath) }
                .onFailure { LogManager.e("CameraCapture", "Review decode failed ${scan.absolutePath}", it) }
                .getOrNull()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, Color.White, CircleShape)
                    .clickable(onClick = onRetake),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Retake",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, Color.White, CircleShape)
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Confirm",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
