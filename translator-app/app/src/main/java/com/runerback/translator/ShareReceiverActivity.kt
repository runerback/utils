package com.runerback.translator

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.runerback.translator.bookshelf.Book
import com.runerback.translator.bookshelf.BookEntry
import com.runerback.translator.bookshelf.BookType
import com.runerback.translator.reader.ReaderActivity
import com.runerback.translator.util.LogManager

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent) {
        LogManager.d("ShareReceiverActivity", "Received action=${intent.action}, type=${intent.type}")
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    null
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                }
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }

        uri?.let {
            val book = createBookFromUri(it)
            startActivity(ReaderActivity.createIntent(this, book))
        }
    }

    private fun createBookFromUri(uri: Uri): Book {
        val name = uri.lastPathSegment ?: "Shared"
        val type = when {
            name.endsWith(".epub", ignoreCase = true) -> BookType.EPUB
            name.endsWith(".pdf", ignoreCase = true) -> BookType.PDF
            name.endsWith(".txt", ignoreCase = true) -> BookType.TXT
            else -> BookType.TXT
        }
        return Book(
            id = java.util.UUID.randomUUID().toString(),
            title = name.substringBeforeLast("."),
            group = null,
            type = type,
            coverUri = null,
            entries = listOf(BookEntry(uri = uri, name = name)),
        )
    }
}
