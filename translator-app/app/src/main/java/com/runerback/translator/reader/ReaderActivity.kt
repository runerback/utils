@file:OptIn(ExperimentalMaterial3Api::class)

package com.runerback.translator.reader

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.runerback.translator.bookshelf.Book
import com.runerback.translator.bookshelf.BookType
import com.runerback.translator.data.SettingsRepository
import com.runerback.translator.ocr.PaddleOcrEngine
import com.runerback.translator.reader.epub.EpubParser
import com.runerback.translator.reader.image.ImageReaderScreen
import com.runerback.translator.reader.image.PdfRendererWrapper
import com.runerback.translator.reader.image.cropWhitespace
import com.runerback.translator.reader.text.TextReaderScreen
import com.runerback.translator.ui.floating.FloatingTranslationPanel
import com.runerback.translator.ui.floating.TranslationPanelViewModel
import com.runerback.translator.ui.floating.TranslationPanelViewModelFactory
import com.runerback.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReaderActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(this) }
    private val viewModel: TranslationPanelViewModel by viewModels {
        TranslationPanelViewModelFactory(settingsRepository)
    }

    private var ocrEngine: PaddleOcrEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initOcr()

        val book = intent.getStringExtra(EXTRA_BOOK)?.let {
            Json.decodeFromString<Book>(it)
        }
        if (book == null || book.entries.isEmpty()) {
            finish()
            return
        }

        setContent {
            TranslatorTheme {
                ReaderScreen(
                    book = book,
                    settingsRepository = settingsRepository,
                    viewModel = viewModel,
                    ocrEngine = ocrEngine,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            ocrEngine?.release()
        }
    }

    private fun initOcr() {
        lifecycleScope.launch(Dispatchers.IO) {
            PaddleOcrEngine.create(this@ReaderActivity).onSuccess {
                ocrEngine = it
            }
        }
    }

    companion object {
        private const val EXTRA_BOOK = "extra_book"

        fun createIntent(context: Context, book: Book): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra(EXTRA_BOOK, Json.encodeToString(book))
            }
        }
    }
}

@Composable
private fun ReaderScreen(
    book: Book,
    settingsRepository: SettingsRepository,
    viewModel: TranslationPanelViewModel,
    ocrEngine: PaddleOcrEngine?,
) {
    val scope = rememberCoroutineScope()

    var pageIndex by remember(book) { mutableIntStateOf(book.lastPage.coerceAtLeast(0)) }
    var totalPages by remember(book) { mutableIntStateOf(1) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showBottomMenu by remember { mutableStateOf(false) }
    var enterCropMode by remember { mutableStateOf(false) }

    fun saveProgress(page: Int) {
        scope.launch {
            settingsRepository.updateBookLastPage(book.id, page)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content (center area, handles its own long-press)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 56.dp, start = 0.dp, end = 0.dp),
        ) {
            ReaderContent(
                book = book,
                pageIndex = pageIndex,
                onPageChange = { newPage, newTotal ->
                    pageIndex = newPage
                    totalPages = newTotal
                    saveProgress(newPage)
                },
                onTotalPages = { totalPages = it },
                viewModel = viewModel,
                ocrEngine = ocrEngine,
                menusVisible = showTopMenu || showBottomMenu,
                enterCropMode = enterCropMode,
                onCropModeHandled = { enterCropMode = false },
            )
        }

        // Pagination indicator
        Text(
            text = "${pageIndex + 1} / $totalPages",
            color = Color.Black,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        )

        // Top zone
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.TopCenter)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showTopMenu = !showTopMenu; showBottomMenu = false })
                },
        )

        // Bottom zone
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showBottomMenu = !showBottomMenu; showTopMenu = false })
                },
        )

        // Left zone
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 56.dp, bottom = 56.dp)
                .fillMaxWidth(0.25f)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (!showTopMenu && !showBottomMenu) {
                            pageIndex = (pageIndex - 1).coerceAtLeast(0)
                            saveProgress(pageIndex)
                        }
                    })
                },
        )

        // Right zone
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 56.dp, bottom = 56.dp)
                .fillMaxWidth(0.25f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (!showTopMenu && !showBottomMenu) {
                            pageIndex = (pageIndex + 1).coerceAtMost(totalPages - 1)
                            saveProgress(pageIndex)
                        }
                    })
                },
        )

        // Top menu placeholder
        if (showTopMenu) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White)
                    .border(2.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = book.title,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Bottom menu placeholder
        if (showBottomMenu) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
                    .border(2.dp, Color.Black)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Page settings",
                    color = Color.Black,
                )
            }
        }

        // Translation panel
        if (viewModel.isVisible) {
            FloatingTranslationPanel(
                state = viewModel.state,
                anchor = viewModel.anchor,
                onSimplify = { viewModel.onSimplify() },
                onChinese = { viewModel.onChinese() },
                onDismiss = { viewModel.dismiss() },
            )
        }
    }
}

@Composable
private fun ReaderContent(
    book: Book,
    pageIndex: Int,
    onPageChange: (Int, Int) -> Unit,
    onTotalPages: (Int) -> Unit,
    viewModel: TranslationPanelViewModel,
    ocrEngine: PaddleOcrEngine?,
    menusVisible: Boolean,
    enterCropMode: Boolean,
    onCropModeHandled: () -> Unit,
) {
    if (book.type == BookType.MANGA) {
        MangaReader(
            book = book,
            pageIndex = pageIndex,
            onPageChange = onPageChange,
            ocrEngine = ocrEngine,
            viewModel = viewModel,
            menusVisible = menusVisible,
            enterCropMode = enterCropMode,
            onCropModeHandled = onCropModeHandled,
        )
    } else {
        SingleEntryReader(
            uri = book.entries.first().uri,
            type = book.type,
            pageIndex = pageIndex,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            viewModel = viewModel,
            ocrEngine = ocrEngine,
            menusVisible = menusVisible,
            enterCropMode = enterCropMode,
            onCropModeHandled = onCropModeHandled,
        )
    }
}

@Composable
private fun MangaReader(
    book: Book,
    pageIndex: Int,
    onPageChange: (Int, Int) -> Unit,
    ocrEngine: PaddleOcrEngine?,
    viewModel: TranslationPanelViewModel,
    menusVisible: Boolean,
    enterCropMode: Boolean,
    onCropModeHandled: () -> Unit,
) {
    val uri = book.entries[pageIndex.coerceIn(0, book.entries.size - 1)].uri

    LaunchedEffect(pageIndex) {
        onPageChange(pageIndex, book.entries.size)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ImageReader(
            uri = uri,
            fileType = FileType.IMAGE,
            pageIndex = 0,
            onPageChange = { _, _ -> },
            onTotalPages = { },
            ocrEngine = ocrEngine,
            viewModel = viewModel,
            menusVisible = menusVisible,
            enterCropMode = enterCropMode,
            onCropModeHandled = onCropModeHandled,
        )
    }
}

@Composable
private fun SingleEntryReader(
    uri: Uri,
    type: BookType,
    pageIndex: Int,
    onPageChange: (Int, Int) -> Unit,
    onTotalPages: (Int) -> Unit,
    viewModel: TranslationPanelViewModel,
    ocrEngine: PaddleOcrEngine?,
    menusVisible: Boolean,
    enterCropMode: Boolean,
    onCropModeHandled: () -> Unit,
) {
    when (type) {
        BookType.EPUB, BookType.TXT -> TextReader(
            uri = uri,
            fileType = fileTypeFromBookType(type),
            pageIndex = pageIndex,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            viewModel = viewModel,
            menusVisible = menusVisible,
        )
        BookType.PDF -> ImageReader(
            uri = uri,
            fileType = FileType.PDF,
            pageIndex = pageIndex,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            ocrEngine = ocrEngine,
            viewModel = viewModel,
            menusVisible = menusVisible,
            enterCropMode = enterCropMode,
            onCropModeHandled = onCropModeHandled,
        )
        else -> {}
    }
}

@Composable
private fun TextReader(
    uri: Uri,
    fileType: FileType,
    pageIndex: Int,
    onPageChange: (Int, Int) -> Unit,
    onTotalPages: (Int) -> Unit,
    viewModel: TranslationPanelViewModel,
    menusVisible: Boolean,
) {
    var content by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(uri) {
        content = withContext(Dispatchers.IO) {
            when (fileType) {
                FileType.EPUB -> {
                    EpubParser(context).parse(uri).getOrNull()
                        ?.chapters?.firstOrNull()?.body ?: ""
                }
                FileType.TXT -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: ""
                }
                else -> ""
            }
        }
    }

    if (content.isNotEmpty()) {
        TextReaderScreen(
            content = content,
            initialPage = pageIndex,
            menusVisible = menusVisible,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            onTranslate = { selected, anchor ->
                if (!menusVisible) viewModel.show(selected, anchor)
            },
        )
    }
}

@Composable
private fun ImageReader(
    uri: Uri,
    fileType: FileType,
    pageIndex: Int,
    onPageChange: (Int, Int) -> Unit,
    onTotalPages: (Int) -> Unit,
    ocrEngine: PaddleOcrEngine?,
    viewModel: TranslationPanelViewModel,
    menusVisible: Boolean,
    enterCropMode: Boolean,
    onCropModeHandled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pdfPageCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(uri, pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            when (fileType) {
                FileType.PDF -> {
                    val renderer = PdfRendererWrapper(context, uri)
                    pdfPageCount = renderer.pageCount.coerceAtLeast(1)
                    onTotalPages(pdfPageCount)
                    val page = renderer.renderPage(pageIndex.coerceIn(0, pdfPageCount - 1), 1200)
                    renderer.close()
                    page?.cropWhitespace()
                }
                FileType.IMAGE -> {
                    onTotalPages(1)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)?.cropWhitespace()
                    }
                }
                else -> null
            }
        }
    }

    bitmap?.let { bmp ->
        ImageReaderScreen(
            bitmap = bmp,
            contentScale = if (fileType == FileType.IMAGE) ContentScale.Crop else ContentScale.Fit,
            menusVisible = menusVisible,
            enterCropMode = enterCropMode,
            onCropModeHandled = onCropModeHandled,
            onCrop = { cropped, _ ->
                scope.launch(Dispatchers.IO) {
                    val engine = ocrEngine ?: return@launch
                    engine.run(cropped).onSuccess { lines ->
                        withContext(Dispatchers.Main) {
                            val text = lines.joinToString("\n")
                            val anchor = Rect(0, 0, 0, 0)
                            viewModel.show(text, anchor)
                        }
                    }
                }
            },
        )
    }
}

private enum class FileType { EPUB, TXT, PDF, IMAGE, UNKNOWN }

private fun fileTypeFromBookType(type: BookType): FileType {
    return when (type) {
        BookType.EPUB -> FileType.EPUB
        BookType.TXT -> FileType.TXT
        BookType.PDF -> FileType.PDF
        BookType.MANGA -> FileType.IMAGE
    }
}
