package com.runerback.translator.camera

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
        // no cropping, no whitespace trimming.
        bitmap?.let { bmp ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.safeDrawing.asPaddingValues()),
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
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
