package com.runerback.translator.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.translator.bookshelf.Book
import com.runerback.translator.bookshelf.BookType
import com.runerback.translator.data.SettingsManager
import com.runerback.translator.data.SettingsRepository
import com.runerback.translator.ocr.OcrEngineProvider
import com.runerback.translator.reader.epub.EpubParser
import com.runerback.translator.reader.image.ImageRangeSelector
import com.runerback.translator.reader.image.ImageReaderScreen
import com.runerback.translator.reader.image.PdfRendererWrapper
import com.runerback.translator.reader.image.cropWhitespace
import com.runerback.translator.reader.pdf.PdfPageCache
import com.runerback.translator.reader.pdf.PdfPaginator
import com.runerback.translator.reader.pdf.PdfTextReaderScreen
import com.runerback.translator.reader.text.TextReaderScreen
import com.runerback.translator.reader.text.textMarginPx
import com.runerback.translator.ui.floating.FloatingTranslationPanel
import com.runerback.translator.ui.floating.TranslationPanelViewModel
import com.runerback.translator.ui.floating.TranslationPanelViewModelFactory
import com.runerback.translator.ui.theme.TranslatorTheme
import com.runerback.translator.util.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()
            }
        }
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

class ReaderActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(this) }
    private val viewModel: TranslationPanelViewModel by viewModels {
        TranslationPanelViewModelFactory(settingsRepository)
    }
    private val readerViewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setWindowAnimations(0)
        super.onCreate(savedInstanceState)

        val book = intent.getStringExtra(EXTRA_BOOK)?.let {
            Json.decodeFromString<Book>(it)
        }
        if (book == null || book.entries.isEmpty()) {
            finish()
            return
        }

        setContent {
            TranslatorTheme {
                CompositionLocalProvider(LocalIndication provides NoIndication) {
                    ReaderScreen(
                        book = book,
                        settingsRepository = settingsRepository,
                        viewModel = viewModel,
                        readerViewModel = readerViewModel,
                    )
                }
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
    readerViewModel: ReaderViewModel,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext

    val readerDebugMode by SettingsManager.readerDebugMode.collectAsStateWithLifecycle(initialValue = false)
    val showSimplifyButton by settingsRepository.showSimplifyButton.collectAsStateWithLifecycle(initialValue = true)

    var pageIndex by remember(book) { mutableIntStateOf(book.lastPage.coerceAtLeast(0)) }
    var targetPageIndex by remember(book) { mutableIntStateOf(pageIndex) }
    var totalPages by remember(book) { mutableIntStateOf(1) }
    var clearingPage by remember(book) { mutableStateOf(false) }
    var ocrBusy by remember { mutableStateOf(false) }
    var currentImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentImageContentScale by remember { mutableStateOf(ContentScale.Fit) }
    var currentPageHasBitmap by remember(book) { mutableStateOf(false) }

    fun saveProgress(page: Int) {
        scope.launch {
            settingsRepository.updateBookLastPage(book.id, page)
        }
    }

    val menusVisible = readerViewModel.menusVisible

    LaunchedEffect(targetPageIndex) {
        if (targetPageIndex == pageIndex) return@LaunchedEffect
        // Frame 1: full white flash to clear e-ink ghosting.
        clearingPage = true
        // Wait exactly one frame so the white flash is drawn.
        withFrameNanos { }
        // Frame 2: switch to the next page and remove the flash.
        pageIndex = targetPageIndex
        readerViewModel.onCropSelectorClosed()
        currentImageBitmap = null
        currentImageContentScale = ContentScale.Fit
        currentPageHasBitmap = false
        clearingPage = false
    }

    LaunchedEffect(totalPages) {
        val maxPage = (totalPages - 1).coerceAtLeast(0)
        if (pageIndex > maxPage) {
            pageIndex = maxPage
            targetPageIndex = maxPage
            saveProgress(pageIndex)
        }
    }

    LaunchedEffect(pageIndex, totalPages) {
        readerViewModel.onPageInfo(pageIndex, totalPages)
    }

    LaunchedEffect(currentPageHasBitmap) {
        readerViewModel.hasBitmap = currentPageHasBitmap
    }

    val navEvent = readerViewModel.navEvent
    LaunchedEffect(navEvent) {
        val event = navEvent ?: return@LaunchedEffect
        readerViewModel.consumeNavEvent()
        if (event.page != pageIndex) {
            targetPageIndex = event.page
            saveProgress(event.page)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Content (center area)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues()),
        ) {
            ReaderContent(
                book = book,
                pageIndex = pageIndex,
                onPageChange = { newPage, newTotal ->
                    LogManager.d("ReaderActivity", "onPageChange page=$newPage total=$newTotal")
                    if (newTotal != totalPages) totalPages = newTotal
                },
                onTotalPages = {
                    LogManager.d("ReaderActivity", "onTotalPages total=$it")
                    totalPages = it
                },
                readerDebugMode = readerDebugMode,
                onBitmapLoaded = { bitmap, scale ->
                    currentImageBitmap = bitmap
                    currentImageContentScale = scale
                    currentPageHasBitmap = bitmap != null
                },
                selectWordRequest = readerViewModel.selectWordRequest,
                clearSelectionSignal = readerViewModel.clearSelectionSignal,
                onSelectWordHandled = readerViewModel::onSelectWordHandled,
                onSelectionChanged = readerViewModel::onTextSelected,
            )
        }

        // Single low-level input collector above the content: it only detects
        // taps and long-presses and forwards them to the ReaderViewModel,
        // which owns every interaction rule. Interactive overlays (toolbar,
        // settings panels, OCR selector, translation panel) sit above it.
        ReaderInputLayer(
            onClick = readerViewModel::onClick,
            onLongPress = readerViewModel::onLongPress,
        )

        // Pagination indicator
        Text(
            text = "${pageIndex + 1} / $totalPages",
            color = Color.Black,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        )

        // Top settings panel
        if (readerViewModel.settings == SettingsPanel.TOP) {
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

        // Bottom settings panel
        if (readerViewModel.settings == SettingsPanel.BOTTOM) {
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

        // Translate toolbar for text selection
        readerViewModel.selection?.let { selected ->
            TranslateToolbar(
                windowAnchor = selected.toolbarAnchor,
                onTranslate = {
                    // The dialog anchors at the long-press point that started
                    // the selection, not at the selection rect.
                    val p = selected.longPressStart
                    viewModel.show(
                        selected.text,
                        Rect(p.x.toInt(), p.y.toInt(), p.x.toInt(), p.y.toInt()),
                    )
                    readerViewModel.clearSelection()
                },
            )
        }

        // Full-screen selector overlay on top of everything while crop mode is active.
        val crop = readerViewModel.cropRequest
        if (crop != null && currentImageBitmap != null) {
            ImageRangeSelector(
                bitmap = currentImageBitmap!!,
                contentScale = currentImageContentScale,
                menusVisible = menusVisible,
                isCropMode = true,
                cropRequest = crop.id to crop.offset,
                onCropModeChanged = { active -> if (!active) readerViewModel.onCropSelectorClosed() },
                onCrop = { cropped, anchorRect ->
                    ocrBusy = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            LogManager.d("ReaderActivity", "OCR crop received ${cropped.width}x${cropped.height}")
                            val engine = OcrEngineProvider.get(context)
                            if (engine == null) {
                                LogManager.e("ReaderActivity", "OCR unavailable: engine init failed")
                                return@launch
                            }
                            engine.run(cropped).onSuccess { lines ->
                                LogManager.d("ReaderActivity", "OCR done lines=${lines.size}")
                                withContext(Dispatchers.Main) {
                                    val text = lines.joinToString("\n")
                                    viewModel.show(text, anchorRect, TranslationPanelViewModel.Source.OCR)
                                }
                            }.onFailure {
                                LogManager.e("ReaderActivity", "OCR failed", it)
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
                        .background(Color.White)
                        .border(2.dp, Color.Black)
                        .padding(16.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recognizing...", color = Color.Black)
                }
            }
        }

        // Translation panel
        if (viewModel.isVisible) {
            FloatingTranslationPanel(
                state = viewModel.state,
                anchor = viewModel.anchor,
                showSimplify = showSimplifyButton,
                onSimplify = { viewModel.onSimplify() },
                onChinese = { viewModel.onChinese() },
                onDismiss = {
                    viewModel.dismiss()
                    readerViewModel.onCropSelectorClosed()
                },
            )
        }

        // E-ink full refresh flash between pages.
        if (clearingPage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
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
    readerDebugMode: Boolean,
    onBitmapLoaded: (Bitmap?, ContentScale) -> Unit,
    selectWordRequest: IndexedOffset?,
    clearSelectionSignal: Int,
    onSelectWordHandled: (Int) -> Unit,
    onSelectionChanged: (String?, Rect?) -> Unit,
) {
    if (book.type == BookType.MANGA) {
        MangaReader(
            book = book,
            pageIndex = pageIndex,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            readerDebugMode = readerDebugMode,
            onBitmapLoaded = onBitmapLoaded,
        )
    } else {
        SingleEntryReader(
            uri = book.entries.first().uri,
            type = book.type,
            pageIndex = pageIndex,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            readerDebugMode = readerDebugMode,
            onBitmapLoaded = onBitmapLoaded,
            selectWordRequest = selectWordRequest,
            clearSelectionSignal = clearSelectionSignal,
            onSelectWordHandled = onSelectWordHandled,
            onSelectionChanged = onSelectionChanged,
        )
    }
}

@Composable
private fun MangaReader(
    book: Book,
    pageIndex: Int,
    onPageChange: (Int, Int) -> Unit,
    onTotalPages: (Int) -> Unit,
    readerDebugMode: Boolean,
    onBitmapLoaded: (Bitmap?, ContentScale) -> Unit,
) {
    val uri = book.entries[pageIndex.coerceIn(0, book.entries.size - 1)].uri

    LaunchedEffect(pageIndex) {
        onPageChange(pageIndex, book.entries.size)
    }

    LaunchedEffect(Unit) {
        LogManager.d("ReaderActivity", "MangaReader entries=${book.entries.size}")
        onTotalPages(book.entries.size)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ImageFileReader(
            uri = uri,
            readerDebugMode = readerDebugMode,
            onBitmapLoaded = onBitmapLoaded,
        )
    }
}

@Composable
private fun ImageFileReader(
    uri: Uri,
    readerDebugMode: Boolean,
    onBitmapLoaded: (Bitmap?, ContentScale) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        bitmap = null
        loadError = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)?.cropWhitespace(debug = readerDebugMode)
                }
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                LogManager.e("ReaderActivity", "Error loading image uri=$uri", e)
                null
            }
        }
        if (result != null) {
            bitmap = result
        } else {
            loadError = true
        }
    }

    bitmap?.let { bmp ->
        LaunchedEffect(bmp) {
            onBitmapLoaded(bmp, ContentScale.Crop)
        }
        ImageReaderScreen(
            bitmap = bmp,
            contentScale = ContentScale.Crop,
            debug = readerDebugMode,
        )
    }

    if (loadError) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Unable to open page",
                color = Color.Black,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun SingleEntryReader(
    uri: Uri,
    type: BookType,
    pageIndex: Int,
    onPageChange: (Int, Int) -> Unit,
    onTotalPages: (Int) -> Unit,
    readerDebugMode: Boolean,
    onBitmapLoaded: (Bitmap?, ContentScale) -> Unit,
    selectWordRequest: IndexedOffset?,
    clearSelectionSignal: Int,
    onSelectWordHandled: (Int) -> Unit,
    onSelectionChanged: (String?, Rect?) -> Unit,
) {
    when (type) {
        BookType.EPUB, BookType.TXT -> TextReader(
            uri = uri,
            fileType = fileTypeFromBookType(type),
            pageIndex = pageIndex,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            selectWordRequest = selectWordRequest,
            clearSelectionSignal = clearSelectionSignal,
            onSelectWordHandled = onSelectWordHandled,
            onSelectionChanged = onSelectionChanged,
        )
        BookType.PDF -> PdfReader(
            uri = uri,
            pageIndex = pageIndex,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            readerDebugMode = readerDebugMode,
            onBitmapLoaded = onBitmapLoaded,
            selectWordRequest = selectWordRequest,
            clearSelectionSignal = clearSelectionSignal,
            onSelectWordHandled = onSelectWordHandled,
            onSelectionChanged = onSelectionChanged,
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
    selectWordRequest: IndexedOffset?,
    clearSelectionSignal: Int,
    onSelectWordHandled: (Int) -> Unit,
    onSelectionChanged: (String?, Rect?) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(uri) {
        content = withContext(Dispatchers.IO) {
            when (fileType) {
                FileType.EPUB -> {
                    val chapters = EpubParser(context).parse(uri).getOrNull()?.chapters
                    val body = chapters?.joinToString("\n\n") { it.body } ?: ""
                    LogManager.d("ReaderActivity", "EPUB chapters=${chapters?.size ?: 0} contentLength=${body.length}")
                    body
                }
                FileType.TXT -> {
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: ""
                    LogManager.d("ReaderActivity", "TXT contentLength=${text.length}")
                    text
                }
                else -> ""
            }
        }
    }

    if (content.isNotEmpty()) {
        TextReaderScreen(
            content = content,
            initialPage = pageIndex,
            selectWordRequest = selectWordRequest,
            clearSelectionSignal = clearSelectionSignal,
            onSelectWordHandled = onSelectWordHandled,
            onPageChange = onPageChange,
            onTotalPages = onTotalPages,
            onSelectionChanged = onSelectionChanged,
        )
    } else {
        LaunchedEffect(Unit) {
            LogManager.d("ReaderActivity", "Empty content for $fileType uri=$uri")
            onTotalPages(0)
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Empty book",
                color = Color.Black,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun PdfReader(
    uri: Uri,
    pageIndex: Int,
    onPageChange: (Int, Int) -> Unit,
    onTotalPages: (Int) -> Unit,
    readerDebugMode: Boolean,
    onBitmapLoaded: (Bitmap?, ContentScale) -> Unit,
    selectWordRequest: IndexedOffset?,
    clearSelectionSignal: Int,
    onSelectWordHandled: (Int) -> Unit,
    onSelectionChanged: (String?, Rect?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var containerSize by remember { mutableStateOf(Size.Zero) }

    val params = remember(density, containerSize) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return@remember null
        PdfPageCache.RenderParams(
            fontSizeSp = 18f,
            lineHeight = 1.3f,
            width = containerSize.width.toInt(),
            height = containerSize.height.toInt(),
            paddingPx = textMarginPx(context, 18f),
        )
    }

    var paginator by remember { mutableStateOf<PdfPaginator?>(null) }

    DisposableEffect(uri, params) {
        val newPaginator = params?.let { PdfPaginator(context, uri, it, scope) }
        paginator = newPaginator
        onDispose {
            newPaginator?.cancel()
        }
    }

    val totalChunks by paginator?.totalChunks?.collectAsStateWithLifecycle()
        ?: remember { mutableIntStateOf(0) }

    LaunchedEffect(pageIndex, totalChunks) {
        onPageChange(pageIndex, totalChunks.coerceAtLeast(1))
    }

    LaunchedEffect(paginator) {
        paginator?.totalChunks?.collect { total ->
            onTotalPages(total)
        }
    }

    var pageLocation by remember { mutableStateOf<PdfPaginator.PageLocation?>(null) }
    var pageText by remember { mutableStateOf("") }
    var isImagePage by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex, paginator) {
        isLoading = true
        val pag = paginator ?: return@LaunchedEffect
        val location = pag.locationFor(pageIndex)
        pageLocation = location
        isImagePage = pag.isImagePage(location.pdfPage)
        pageText = if (isImagePage) "" else pag.chunkText(pageIndex)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .onSizeChanged { containerSize = it.toSize() },
    ) {
        if (isLoading || paginator == null) {
            return@Box
        }

        val location = pageLocation ?: return@Box

        LaunchedEffect(isImagePage, location.pdfPage) {
            if (!isImagePage) {
                onBitmapLoaded(null, ContentScale.Fit)
            }
        }

        if (isImagePage) {
            PdfBitmapPage(
                uri = uri,
                pdfPage = location.pdfPage,
                readerDebugMode = readerDebugMode,
                onBitmapLoaded = onBitmapLoaded,
            )
        } else {
            PdfTextReaderScreen(
                text = pageText,
                images = emptyList(),
                pageIndex = pageIndex,
                totalPages = totalChunks,
                selectWordRequest = selectWordRequest,
                clearSelectionSignal = clearSelectionSignal,
                onSelectWordHandled = onSelectWordHandled,
                onPageChange = onPageChange,
                onTotalPages = onTotalPages,
                onSelectionChanged = onSelectionChanged,
                debug = readerDebugMode,
            )
        }
    }
}

@Composable
private fun PdfBitmapPage(
    uri: Uri,
    pdfPage: Int,
    readerDebugMode: Boolean,
    onBitmapLoaded: (Bitmap?, ContentScale) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri, pdfPage) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(uri, pdfPage) { mutableStateOf(false) }

    LaunchedEffect(uri, pdfPage) {
        bitmap = null
        loadError = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val renderer = PdfRendererWrapper(context, uri)
                val count = renderer.pageCount
                if (count <= 0) {
                    renderer.close()
                    null
                } else {
                    val page = renderer.renderPage(pdfPage.coerceIn(0, count - 1), 1200)
                    renderer.close()
                    page?.cropWhitespace(debug = readerDebugMode)
                }
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                LogManager.e("ReaderActivity", "Error rendering PDF bitmap page=$pdfPage uri=$uri", e)
                null
            }
        }
        if (result != null) {
            bitmap = result
        } else {
            loadError = true
        }
    }

    bitmap?.let { bmp ->
        LaunchedEffect(bmp) {
            onBitmapLoaded(bmp, ContentScale.Fit)
        }
        ImageReaderScreen(
            bitmap = bmp,
            contentScale = ContentScale.Fit,
            debug = readerDebugMode,
        )
    }

    if (loadError) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Unable to open page",
                color = Color.Black,
                fontSize = 16.sp,
            )
        }
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

@Composable
private fun TranslateToolbar(
    windowAnchor: Rect,
    onTranslate: () -> Unit,
) {
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val toolbarGap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val centerX = (windowAnchor.left + windowAnchor.right) / 2
    val x = centerX - toolbarSize.width / 2
    val y = if (windowAnchor.top - toolbarSize.height - toolbarGap >= 0) {
        windowAnchor.top - toolbarSize.height - toolbarGap
    } else {
        windowAnchor.bottom + toolbarGap
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(x, y) }
            .onSizeChanged { toolbarSize = it }
            .wrapContentSize()
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .clickable(onClick = onTranslate)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Translate",
            color = Color.Black,
            fontSize = 14.sp,
        )
    }
}
