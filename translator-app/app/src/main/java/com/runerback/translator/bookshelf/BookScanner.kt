package com.runerback.translator.bookshelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.runerback.translator.reader.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BookScanner(private val context: Context) {

    private val contentResolver = context.contentResolver
    private val thumbnailDir by lazy {
        File(context.cacheDir, "thumbnails").also { it.mkdirs() }
    }

    suspend fun scan(rootUri: Uri): Result<List<Book>> = withContext(Dispatchers.IO) {
        runCatching {
            val rootTreeUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                rootUri,
                DocumentsContract.getTreeDocumentId(rootUri),
            )
            scanDirectory(rootTreeUri, group = null)
        }
    }

    private suspend fun scanDirectory(dirUri: Uri, group: String?): List<Book> = withContext(Dispatchers.IO) {
        val children = queryDocuments(dirUri)
        val books = mutableListOf<Book>()
        val imageEntries = mutableListOf<BookEntry>()

        children.forEach { doc ->
            when {
                doc.isDirectory -> {
                    val childGroup = if (group == null) doc.name else "$group/${doc.name}"
                    val subBooks = scanDirectory(doc.uri, childGroup)
                    books.addAll(subBooks)
                }
                isImage(doc.name) -> {
                    imageEntries.add(BookEntry(uri = doc.uri, name = doc.name))
                }
                isBookFile(doc.name) -> {
                    books.add(createBook(doc, group))
                }
            }
        }

        if (imageEntries.isNotEmpty()) {
            val mangaBook = createMangaBook(imageEntries, group)
            books.add(mangaBook)
        }

        books
    }

    private suspend fun createBook(doc: DocumentInfo, group: String?): Book {
        val type = when {
            doc.name.endsWith(".epub", ignoreCase = true) -> BookType.EPUB
            doc.name.endsWith(".pdf", ignoreCase = true) -> BookType.PDF
            doc.name.endsWith(".txt", ignoreCase = true) -> BookType.TXT
            else -> BookType.TXT
        }

        val coverUri = when (type) {
            BookType.PDF -> generatePdfThumbnail(doc.uri, 0)
            BookType.EPUB -> generateEpubThumbnail(doc.uri)
            else -> null
        }

        val title = doc.name.substringBeforeLast(".")

        return Book(
            id = UUID.randomUUID().toString(),
            title = title,
            group = group,
            type = type,
            coverUri = coverUri,
            entries = listOf(BookEntry(uri = doc.uri, name = doc.name)),
        )
    }

    private suspend fun createMangaBook(entries: List<BookEntry>, group: String?): Book {
        val coverUri = entries.firstOrNull()?.uri
        val folderName = group?.substringAfterLast("/") ?: "Manga"
        return Book(
            id = UUID.randomUUID().toString(),
            title = folderName,
            group = group,
            type = BookType.MANGA,
            coverUri = coverUri,
            entries = entries.sortedBy { it.name },
        )
    }

    private fun parseThumbnailPage(name: String): Pair<String, Int> {
        val regex = Regex("#page(\\d+)", RegexOption.IGNORE_CASE)
        val match = regex.find(name)
        val page = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1)?.minus(1) ?: 0
        val cleanName = name.replace(regex, "")
        return cleanName to page
    }

    suspend fun generatePdfThumbnail(uri: Uri, pageIndex: Int): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val fileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val renderer = PdfRenderer(fileDescriptor)
            if (renderer.pageCount == 0) {
                renderer.close()
                return@withContext null
            }
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            val page = renderer.openPage(safeIndex)
            val bitmap = Bitmap.createBitmap(200, (200f / page.width * page.height).toInt(), Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            saveThumbnail(bitmap)
        }.getOrNull()
    }

    suspend fun getPdfPageCount(uri: Uri): Int? = withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrNull()
    }

    private suspend fun generateEpubThumbnail(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        // TODO: extract cover image from EPUB
        null
    }

    private fun saveThumbnail(bitmap: Bitmap): Uri? {
        val file = File(thumbnailDir, "thumb_${System.currentTimeMillis()}.png")
        return runCatching {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            Uri.fromFile(file)
        }.getOrNull()
    }

    private fun queryDocuments(parentUri: Uri): List<DocumentInfo> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getDocumentId(parentUri),
        )
        val results = mutableListOf<DocumentInfo>()
        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex)
                val mime = cursor.getString(mimeIndex)
                val uri = DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                results.add(
                    DocumentInfo(
                        uri = uri,
                        name = name,
                        mimeType = mime,
                        isDirectory = DocumentsContract.Document.MIME_TYPE_DIR == mime,
                    ),
                )
            }
        }
        return results
    }

    private fun isImage(name: String): Boolean {
        return name.endsWith(".png", ignoreCase = true) ||
            name.endsWith(".jpg", ignoreCase = true) ||
            name.endsWith(".jpeg", ignoreCase = true) ||
            name.endsWith(".webp", ignoreCase = true)
    }

    private fun isBookFile(name: String): Boolean {
        return name.endsWith(".epub", ignoreCase = true) ||
            name.endsWith(".pdf", ignoreCase = true) ||
            name.endsWith(".txt", ignoreCase = true)
    }

    private data class DocumentInfo(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val isDirectory: Boolean,
    )
}
