package com.runerback.translator.dictionary

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import space.subread.dictionary.core.DictionaryFormatException
import space.subread.dictionary.core.DictionarySink
import space.subread.dictionary.core.Tag
import space.subread.dictionary.core.Term
import space.subread.dictionary.core.TermMeta
import space.subread.dictionary.core.YomitanZip
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Imports a Yomitan format-3 dictionary zip into the store. The zip is copied to a cache
 * file first: `YomitanZip` reads it twice (index, then banks) and SAF streams are not
 * guaranteed to reopen. The dictionary row and all its rows are written in one transaction,
 * so a failure or cancellation leaves nothing behind.
 */
object DictionaryImporter {

    private const val PROGRESS_INTERVAL = 500

    /**
     * Imports the zip at [uri]; [onProgress] reports the inserted row count every
     * [PROGRESS_INTERVAL] rows and once more at the end. Throws [DictionaryFormatException]
     * for zips that are not format-3 dictionaries, [IOException] for read failures, and
     * [kotlin.coroutines.cancellation.CancellationException] when cancelled.
     */
    suspend fun importDictionary(
        context: Context,
        store: DictionaryStore,
        uri: Uri,
        onProgress: (rows: Int) -> Unit,
    ): DictionaryInfo = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "dict-import-${UUID.randomUUID()}.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Cannot open the selected file")

            val index = temp.inputStream().buffered().use { YomitanZip.readIndex(it) }
            if (index.format != 3) {
                throw DictionaryFormatException("Unsupported dictionary format ${index.format} — only format 3 is supported")
            }

            val jobContext = coroutineContext
            val db = store.writableDatabase
            db.beginTransaction()
            var rows = 0
            try {
                val dictionaryId = store.insertDictionary(db, index.title, index.revision)
                val sink = store.importSink(db, dictionaryId)
                temp.inputStream().buffered().use { input ->
                    YomitanZip.readBanks(input, object : DictionarySink {
                        private fun row() {
                            rows++
                            if (rows % PROGRESS_INTERVAL == 0) {
                                jobContext.ensureActive()
                                onProgress(rows)
                            }
                        }

                        override fun term(term: Term) {
                            sink.term(term)
                            row()
                        }

                        override fun termMeta(meta: TermMeta) {
                            sink.termMeta(meta)
                            row()
                        }

                        override fun tag(tag: Tag) = sink.tag(tag)
                    })
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            onProgress(rows)
            store.list().first { it.title == index.title }
        } finally {
            temp.delete()
        }
    }
}
