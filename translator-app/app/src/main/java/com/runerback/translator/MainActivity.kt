@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.runerback.translator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.runerback.translator.bookshelf.Book
import com.runerback.translator.bookshelf.BookScanner
import com.runerback.translator.bookshelf.BookSort
import com.runerback.translator.bookshelf.BookType
import com.runerback.translator.data.SettingsRepository
import com.runerback.translator.reader.ReaderActivity
import com.runerback.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TranslatorTheme {
                BookshelfScreen()
            }
        }
    }
}

@Composable
private fun BookshelfScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }

    val books by settingsRepository.books.collectAsState(initial = emptyList())
    val rootFolder by settingsRepository.rootFolderUri.collectAsState(initial = null)
    val bookSort by settingsRepository.bookSort.collectAsState(initial = BookSort.NAME_ASC)

    var isScanning by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var pdfDialog by remember { mutableStateOf<Pair<Book, Int>?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            scope.launch {
                settingsRepository.setRootFolderUri(it)
                isScanning = true
                val existing = settingsRepository.books.first()
                val result = BookScanner(context).scan(it)
                result.onSuccess { scanned ->
                    settingsRepository.setBooks(mergeScannedBooks(existing, scanned))
                }
                isScanning = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                ),
                actions = {
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = "Choose folder",
                            tint = Color.Black,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            rootFolder?.let { uri ->
                                scope.launch {
                                    isScanning = true
                                    val existing = settingsRepository.books.first()
                                    val result = BookScanner(context).scan(uri)
                                    result.onSuccess { scanned ->
                                        settingsRepository.setBooks(mergeScannedBooks(existing, scanned))
                                    }
                                    isScanning = false
                                }
                            }
                        },
                        enabled = rootFolder != null && !isScanning,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Rescan",
                            tint = Color.Black,
                        )
                    }
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                            contentDescription = "Sort",
                            tint = Color.Black,
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        BookSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sortLabel(sort)) },
                                onClick = {
                                    scope.launch { settingsRepository.setBookSort(sort) }
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showSettings = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color.Black,
                        )
                    }
                    OutlinedButton(
                        onClick = { showLogs = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "Logs",
                            tint = Color.Black,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isScanning -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Color.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Scanning...", color = Color.Black)
                    }
                }
                books.isEmpty() -> {
                    EmptyState(onPickFolder = { folderPicker.launch(null) })
                }
                else -> {
                    BookshelfGrid(
                        books = books,
                        sort = bookSort,
                        onBookClick = { book ->
                            context.startActivity(ReaderActivity.createIntent(context, book))
                        },
                        onBookLongClick = { book ->
                            if (book.type == BookType.PDF) {
                                scope.launch(Dispatchers.IO) {
                                    val count = BookScanner(context).getPdfPageCount(book.entries.first().uri)
                                    withContext(Dispatchers.Main) {
                                        count?.let { pdfDialog = book to it }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                settingsRepository = settingsRepository,
                onDismiss = { showSettings = false },
            )
        }

        if (showLogs) {
            LogsDialog(onDismiss = { showLogs = false })
        }

        pdfDialog?.let { (book, pageCount) ->
            PdfThumbnailDialog(
                pageCount = pageCount,
                initialPage = book.thumbnailPage.takeIf { it > 0 }?.coerceIn(1, pageCount) ?: 1,
                onDismiss = { pdfDialog = null },
                onConfirm = { page ->
                    pdfDialog = null
                    scope.launch(Dispatchers.IO) {
                        val pageIndex = page - 1
                        val coverUri = BookScanner(context).generatePdfThumbnail(
                            book.entries.first().uri,
                            pageIndex,
                        )
                        coverUri?.let { settingsRepository.updateBookThumbnail(book.id, it, pageIndex) }
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyState(onPickFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No books found",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Pick a folder containing your EPUB, TXT, PDF, or manga images.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = onPickFolder,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        ) {
            Text("Choose folder")
        }
    }
}

@Composable
private fun BookshelfGrid(
    books: List<Book>,
    sort: BookSort,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
) {
    val grouped = books.groupBy { it.group ?: "" }
    val sortedGroups = when (sort) {
        BookSort.NAME_DESC -> grouped.toSortedMap(compareByDescending { it })
        else -> grouped.toSortedMap()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        sortedGroups.forEach { (group, groupBooks) ->
            val sortedBooks = when (sort) {
                BookSort.NAME_ASC -> groupBooks.sortedBy { it.title.lowercase() }
                BookSort.NAME_DESC -> groupBooks.sortedByDescending { it.title.lowercase() }
                BookSort.TYPE -> groupBooks.sortedWith(compareBy({ it.type }, { it.title.lowercase() }))
            }
            if (group.isNotBlank()) {
                item {
                    Text(
                        text = group,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            item {
                Shelf(
                    books = sortedBooks,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                )
            }
        }
    }
}

@Composable
private fun Shelf(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .drawBehind {
                val strokeWidth = 4.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth,
                )
            },
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            books.forEach { book ->
                BookCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongClick = { onBookLongClick(book) },
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            }
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(120.dp)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .border(2.dp, Color.Black)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (book.coverUri != null) {
                AsyncImage(
                    model = book.coverUri,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val placeholder = when (book.type) {
                    BookType.EPUB, BookType.TXT -> book.title
                    else -> placeholderText(book.type)
                }
                Text(
                    text = placeholder,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = book.title,
            color = Color.Black,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun placeholderText(type: BookType): String {
    return when (type) {
        BookType.EPUB -> "EPUB"
        BookType.TXT -> "TXT"
        BookType.PDF -> "PDF"
        BookType.MANGA -> "MANGA"
    }
}

private fun sortLabel(sort: BookSort): String {
    return when (sort) {
        BookSort.NAME_ASC -> "Name (A-Z)"
        BookSort.NAME_DESC -> "Name (Z-A)"
        BookSort.TYPE -> "Type"
    }
}

private fun mergeScannedBooks(existing: List<Book>, scanned: List<Book>): List<Book> {
    return scanned.map { newBook ->
        existing.find { oldBook ->
            oldBook.type == newBook.type && oldBook.entries == newBook.entries
        }?.let { oldBook ->
            newBook.copy(
                lastPage = oldBook.lastPage,
                thumbnailPage = oldBook.thumbnailPage,
                coverUri = oldBook.coverUri,
            )
        } ?: newBook
    }
}
