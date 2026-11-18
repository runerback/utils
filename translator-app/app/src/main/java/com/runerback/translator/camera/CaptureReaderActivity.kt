package com.runerback.translator.camera

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.translator.data.SettingsRepository
import com.runerback.translator.ocr.OcrEngineProvider
import com.runerback.translator.reader.image.ImageRangeSelector
import com.runerback.translator.ui.floating.FloatingTranslationPanel
import com.runerback.translator.ui.floating.TranslationPanelViewModel
import com.runerback.translator.ui.floating.TranslationPanelViewModelFactory
import com.runerback.translator.ui.theme.TranslatorTheme
import com.runerback.translator.util.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Display zoom ceiling for the photo view before range selection. */
private const val MaxZoom = 5f

/**
 * The camera capture feature's own reader: shows the captured photo in full
 * (Fit, never cropped, never whitespace-trimmed) and routes long-press range
 * selections through PaddleOCR into the floating translation panel. Shares
 * nothing with the manga reader beyond feature-agnostic infrastructure
 * (OCR engine, translation panel, range selector overlay).
 */
class CaptureReaderActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(this) }
    private val viewModel: TranslationPanelViewModel by viewModels {
        TranslationPanelViewModelFactory(this, settingsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setWindowAnimations(0)
        super.onCreate(savedInstanceState)

        val capturePath = intent.getStringExtra(EXTRA_CAPTURE_PATH)
        if (capturePath == null) {
            finish()
            return
        }

        setContent {
            TranslatorTheme {
                CaptureReaderScreen(
                    capture = File(capturePath),
                    settingsRepository = settingsRepository,
                    viewModel = viewModel,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_CAPTURE_PATH = "extra_capture_path"

        fun createIntent(context: Context, capture: File): Intent {
            return Intent(context, CaptureReaderActivity::class.java).apply {
                putExtra(EXTRA_CAPTURE_PATH, capture.absolutePath)
            }
        }
    }
}

@Composable
private fun CaptureReaderScreen(
    capture: File,
    settingsRepository: SettingsRepository,
    viewModel: TranslationPanelViewModel,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val showSimplifyButton by settingsRepository.showSimplifyButton.collectAsStateWithLifecycle(initialValue = true)

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var ocrBusy by remember { mutableStateOf(false) }
    var cropRequest by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var cropId by remember { mutableIntStateOf(0) }

    // Display zoom over the fit layout, pivoting at the content area's
    // top-left: screen = zoomScale * fitPoint + zoomOffset. Purely visual;
    // the stored photo is never cropped. Zooming only happens in zoom mode,
    // which is entered from the range selector's zoom toggle and exited via
    // the system back action — so zoom gestures never share the screen with
    // any other gesture layer.
    var zoomScale by remember { mutableStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomMode by remember { mutableStateOf(false) }
    val zoomGesturesEnabled = zoomMode && !viewModel.isVisible

    // While zoom mode is on, the system back action (edge swipe / back
    // button) exits zoom mode — keeping the zoom — instead of finishing the
    // activity. Disabled in every other state, so back behaves as before.
    BackHandler(enabled = zoomMode) { zoomMode = false }

    // Closing the range selector ends any zoom session.
    LaunchedEffect(cropRequest) {
        if (cropRequest == null) {
            zoomMode = false
            zoomScale = 1f
            zoomOffset = Offset.Zero
        }
    }

    // Keeps the zoomed image covering the viewport: when a dimension fits,
    // it stays centered; otherwise it can pan but never past the edges.
    fun clampedOffset(scale: Float, offset: Offset): Offset {
        val bmp = bitmap ?: return Offset.Zero
        val contentWidth = viewSize.width.toFloat()
        val contentHeight = viewSize.height.toFloat()
        if (contentWidth <= 0f || contentHeight <= 0f) return offset
        val fitScale = minOf(contentWidth / bmp.width, contentHeight / bmp.height)
        val fitWidth = bmp.width * fitScale
        val fitHeight = bmp.height * fitScale
        val fitLeft = (contentWidth - fitWidth) / 2f
        val fitTop = (contentHeight - fitHeight) / 2f

        fun clampAxis(value: Float, fitOrigin: Float, fitLength: Float, viewLength: Float): Float {
            val displayed = fitLength * scale
            val base = scale * fitOrigin
            return if (displayed <= viewLength) {
                (viewLength - displayed) / 2f - base
            } else {
                value.coerceIn(viewLength - displayed - base, -base)
            }
        }

        return Offset(
            clampAxis(offset.x, fitLeft, fitWidth, contentWidth),
            clampAxis(offset.y, fitTop, fitHeight, contentHeight),
        )
    }

    LaunchedEffect(capture) {
        bitmap = null
        loadError = false
        val decoded = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(capture.absolutePath) }
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    LogManager.e("CaptureReader", "Error decoding capture ${capture.absolutePath}", e)
                    null
                }
        }
        if (decoded != null) {
            bitmap = decoded
        } else {
            loadError = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // The whole photo, always fully visible: Fit into the safe area,
        // no cropping, no whitespace trimming. Display zoom sits on top.
        bitmap?.let { bmp ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .onSizeChanged { viewSize = it }
                    // Pinch-to-zoom (around the pinch centroid) and pan. Only
                    // active in zoom mode, where nothing else handles input.
                    .pointerInput(zoomGesturesEnabled) {
                        if (!zoomGesturesEnabled) return@pointerInput
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, MaxZoom)
                            val applied = newScale / zoomScale
                            val raw = Offset(
                                centroid.x - (centroid.x - zoomOffset.x) * applied + pan.x,
                                centroid.y - (centroid.y - zoomOffset.y) * applied + pan.y,
                            )
                            zoomScale = newScale
                            zoomOffset = clampedOffset(newScale, raw)
                        }
                    },
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = zoomOffset.x
                            translationY = zoomOffset.y
                        },
                )
            }
        }

        if (loadError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Unable to open capture",
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }

        // Long-press anywhere opens the range selector at that point. Disabled
        // while the selector or the translation panel is up.
        if (bitmap != null && cropRequest == null && !viewModel.isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { point ->
                                cropId += 1
                                cropRequest = cropId to point
                            },
                        )
                    },
            )
        }

        val crop = cropRequest
        val bmp = bitmap
        if (crop != null && bmp != null) {
            ImageRangeSelector(
                bitmap = bmp,
                contentScale = ContentScale.Fit,
                zoomScale = zoomScale,
                zoomOffset = zoomOffset,
                zoomMode = zoomMode,
                menusVisible = false,
                isCropMode = true,
                cropRequest = crop,
                onCropModeChanged = { active -> if (!active) cropRequest = null },
                onCrop = { cropped, anchorRect ->
                    ocrBusy = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            LogManager.d("CaptureReader", "OCR crop received ${cropped.width}x${cropped.height}")
                            val engine = OcrEngineProvider.get(context)
                            if (engine == null) {
                                LogManager.e("CaptureReader", "OCR unavailable: engine init failed")
                                return@launch
                            }
                            engine.run(cropped).onSuccess { lines ->
                                LogManager.d("CaptureReader", "OCR done lines=${lines.size}")
                                withContext(Dispatchers.Main) {
                                    val text = lines.joinToString("\n")
                                    viewModel.show(text, anchorRect, TranslationPanelViewModel.Source.OCR)
                                }
                            }.onFailure {
                                LogManager.e("CaptureReader", "OCR failed", it)
                            }
                        } finally {
                            withContext(Dispatchers.Main) { ocrBusy = false }
                        }
                    }
                },
            )
        }

        // Zoom mode toggle (Yomitan picture mode only): visible while the
        // range selector is open. Entering zoom mode hides every popup so
        // pinch/pan get a clean view; the system back action exits back to
        // the selector. Fixed corner position so it never rides the crop rect.
        if (cropRequest != null && !viewModel.isVisible && !zoomMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(48.dp)
                    .border(1.dp, Color.White, CircleShape)
                    .clickable { zoomMode = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ZoomIn,
                    contentDescription = "Zoom",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // OCR busy indicator: first engine init can take a few seconds.
        if (ocrBusy) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black)
                        .border(2.dp, Color.White)
                        .padding(16.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recognizing...", color = Color.White)
                }
            }
        }

        if (viewModel.isVisible) {
            FloatingTranslationPanel(
                state = viewModel.state,
                anchor = viewModel.anchor,
                showSimplify = showSimplifyButton,
                onSimplify = { viewModel.onSimplify() },
                onChinese = { viewModel.onChinese() },
                onDismiss = {
                    viewModel.dismiss()
                    cropRequest = null
                },
                showActions = viewModel.showActionButtons,
                onWordTap = viewModel::onWordTapped,
            )
        }
    }
}
