@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.runerback.translator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
    var selectedGroup by remember { mutableStateOf<Pair<String, List<Book>>?>(null) }

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

    BackHandler(enabled = selectedGroup != null) {
        selectedGroup = null
    }

    Scaffold(
        topBar = {
            selectedGroup?.let { group ->
                GroupTopBar(
                    groupName = group.first,
                    onBack = { selectedGroup = null },
                )
            } ?: MainTopBar(
                rootFolder = rootFolder,
                isScanning = isScanning,
                showSortMenu = showSortMenu,
                onShowSortMenu = { showSortMenu = it },
                bookSort = bookSort,
                onSortSelected = { sort ->
                    scope.launch { settingsRepository.setBookSort(sort) }
                },
                onPickFolder = { folderPicker.launch(null) },
                onRescan = {
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
                onSettings = { showSettings = true },
                onLogs = { showLogs = true },
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
                selectedGroup != null -> {
                    GroupDetailContent(
                        books = selectedGroup!!.second,
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
                else -> {
                    BookshelfGrid(
                        books = books,
                        sort = bookSort,
                        onGroupClick = { group, groupBooks ->
                            selectedGroup = group to groupBooks
                        },
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
private fun MainTopBar(
    rootFolder: Uri?,
    isScanning: Boolean,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    bookSort: BookSort,
    onSortSelected: (BookSort) -> Unit,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onSettings: () -> Unit,
    onLogs: () -> Unit,
) {
    TopAppBar(
        title = { },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = Color.Black,
        ),
        actions = {
            OutlinedButton(
                onClick = onPickFolder,
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
                onClick = onRescan,
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
                onClick = { onShowSortMenu(true) },
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
                onDismissRequest = { onShowSortMenu(false) },
            ) {
                BookSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(sort)) },
                        onClick = {
                            onSortSelected(sort)
                            onShowSortMenu(false)
                        },
                    )
                }
            }
            OutlinedButton(
                onClick = onSettings,
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
                onClick = onLogs,
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
}

@Composable
private fun GroupTopBar(
    groupName: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = groupName,
                color = Color.Black,
            )
        },
        navigationIcon = {
            OutlinedButton(
                onClick = onBack,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = Color.Black,
        ),
    )
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
    onGroupClick: (String, List<Book>) -> Unit,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
) {
    val grouped = books.groupBy { it.group ?: "" }
    val sortedGroups = when (sort) {
        BookSort.NAME_DESC -> grouped.toSortedMap(compareByDescending { it })
        else -> grouped.toSortedMap()
    }
    val ungroupedBooks = sortBooks(grouped[""] ?: emptyList(), sort)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardWidth = 120.dp
        val cardSpacing = 12.dp
        val rowSpacing = 16.dp
        val pageIndicatorHeight = 48.dp
        val groupHeaderHeight = 24.dp
        val coverHeight = cardWidth / 0.7f
        // BookCard = cover + outer padding + spacer + title line
        val cardTotalHeight = coverHeight + 8.dp + 8.dp + 28.dp
        val groupRowHeight = groupHeaderHeight + coverHeight
        val rowHeight = maxOf(groupRowHeight, cardTotalHeight)
        val availableHeight = maxHeight - pageIndicatorHeight
        val rowsPerPage = maxOf(1, (availableHeight / (rowHeight + rowSpacing)).toInt())
        val itemsPerRow = maxOf(1, ((maxWidth + cardSpacing) / (cardWidth + cardSpacing)).toInt())

        val rows = buildList {
            sortedGroups.forEach { (group, groupBooks) ->
                if (group.isNotBlank()) {
                    add(BookshelfRow.Group(group, sortBooks(groupBooks, sort)))
                }
            }
            ungroupedBooks.forEach { book ->
                add(BookshelfRow.Ungrouped(listOf(book)))
            }
        }

        var page by remember { mutableIntStateOf(0) }
        val pageCount = (rows.size + rowsPerPage - 1) / rowsPerPage
        val safePage = page.coerceIn(0, maxOf(0, pageCount - 1))
        val startIndex = safePage * rowsPerPage
        val pageRows = rows.subList(startIndex, minOf(startIndex + rowsPerPage, rows.size))

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                pageRows.forEach { row ->
                    when (row) {
                        is BookshelfRow.Group -> GroupRow(
                            name = row.name,
                            books = row.books,
                            itemsPerRow = itemsPerRow,
                            onClick = { onGroupClick(row.name, row.books) },
                            onBookClick = onBookClick,
                        )
                        is BookshelfRow.Ungrouped -> UngroupedRow(
                            books = row.books,
                            onBookClick = onBookClick,
                            onBookLongClick = onBookLongClick,
                        )
                    }
                }
            }
            if (pageCount > 1) {
                PageIndicator(
                    pageCount = pageCount,
                    currentPage = safePage,
                    onPageSelected = { page = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupDetailContent(
    books: List<Book>,
    sort: BookSort,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
) {
    val sortedBooks = sortBooks(books, sort)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardWidth = 120.dp
        val cardSpacing = 12.dp
        val rowSpacing = 16.dp
        val pageIndicatorHeight = 48.dp
        val coverHeight = cardWidth / 0.7f
        val cardTotalHeight = coverHeight + 8.dp + 8.dp + 28.dp
        val availableHeight = maxHeight - pageIndicatorHeight
        val rowsPerPage = maxOf(1, (availableHeight / (cardTotalHeight + rowSpacing)).toInt())
        val itemsPerRow = maxOf(1, ((maxWidth + cardSpacing) / (cardWidth + cardSpacing)).toInt())

        val rows = sortedBooks.chunked(itemsPerRow)

        var page by remember { mutableIntStateOf(0) }
        val pageCount = (rows.size + rowsPerPage - 1) / rowsPerPage
        val safePage = page.coerceIn(0, maxOf(0, pageCount - 1))
        val startIndex = safePage * rowsPerPage
        val pageRows = rows.subList(startIndex, minOf(startIndex + rowsPerPage, rows.size))

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                pageRows.forEach { rowBooks ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(cardSpacing, Alignment.Start),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowBooks.forEach { book ->
                            BookCard(
                                book = book,
                                onClick = { onBookClick(book) },
                                onLongClick = { onBookLongClick(book) },
                                modifier = Modifier.width(cardWidth),
                            )
                        }
                    }
                }
            }
            if (pageCount > 1) {
                PageIndicator(
                    pageCount = pageCount,
                    currentPage = safePage,
                    onPageSelected = { page = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupRow(
    name: String,
    books: List<Book>,
    itemsPerRow: Int,
    onClick: () -> Unit,
    onBookClick: (Book) -> Unit,
) {
    val coverWidth = 120.dp
    val overflow = books.size > itemsPerRow
    val displayedBooks = if (overflow) books.take(itemsPerRow - 1) else books

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            Box(
                modifier = Modifier
                    .border(1.dp, Color.Black)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${books.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
            modifier = Modifier.fillMaxWidth(),
        ) {
            displayedBooks.forEach { book ->
                BookCover(
                    book = book,
                    modifier = Modifier
                        .width(coverWidth)
                        .clickable { onBookClick(book) },
                )
            }
            if (overflow) {
                Box(
                    modifier = Modifier
                        .width(coverWidth)
                        .aspectRatio(0.7f)
                        .border(2.dp, Color.Black)
                        .clickable { onClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "...",
                        color = Color.Black,
                        fontSize = 20.sp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun UngroupedRow(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        books.forEach { book ->
            BookCard(
                book = book,
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) },
                modifier = Modifier.width(120.dp),
            )
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
                    .border(1.dp, Color.Black)
                    .background(if (selected) Color.Black else Color.White)
                    .clickable { onPageSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    color = if (selected) Color.White else Color.Black,
                    fontSize = 14.sp,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(120.dp)
            .aspectRatio(0.7f)
            .border(2.dp, Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (book.coverUri != null) {
            AsyncImage(
                model = book.coverUri,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
}

private fun sortBooks(books: List<Book>, sort: BookSort): List<Book> {
    return when (sort) {
        BookSort.NAME_ASC -> books.sortedBy { it.title.lowercase() }
        BookSort.NAME_DESC -> books.sortedByDescending { it.title.lowercase() }
        BookSort.TYPE -> books.sortedWith(compareBy({ it.type }, { it.title.lowercase() }))
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

private sealed class BookshelfRow {
    data class Group(val name: String, val books: List<Book>) : BookshelfRow()
    data class Ungrouped(val books: List<Book>) : BookshelfRow()
}
