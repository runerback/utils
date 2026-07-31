package com.runerback.translator.reader.pdf

import android.content.Context
import android.net.Uri
import com.runerback.translator.util.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Maps a global reader chunk index to a PDF page and local chunk.
 *
 * Each PDF page contributes either:
 * - one chunk for image-only pages, or
 * - N chunks for text pages, where N is computed from the page text and the
 *   current rendering parameters.
 *
 * Chunk counts are computed on demand and cached. A background job also fills
 * in counts for the remaining pages so the total stabilizes quickly.
 */
class PdfPaginator(
    context: Context,
    private val uri: Uri,
    private val params: PdfPageCache.RenderParams,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val cache = PdfPageCache(appContext)

    private val job = SupervisorJob()
    private val workerScope = CoroutineScope(scope.coroutineContext + job)

    private val _totalPdfPages = MutableStateFlow(1)
    val totalPdfPages: StateFlow<Int> = _totalPdfPages.asStateFlow()

    private val chunkCountsLock = Any()
    private val chunkCounts = mutableMapOf<Int, Int>()

    private val _totalChunks = MutableStateFlow(0)
    val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        workerScope.launch {
            val count = withContext(Dispatchers.IO) {
                PdfContentExtractor(appContext).pageCount(uri) ?: 1
            }
            _totalPdfPages.value = count.coerceAtLeast(1)
            computeRemainingChunks()
        }
    }

    /**
     * Cancels the background chunk computation. The paginator should be
     * discarded after calling this.
     */
    fun cancel() {
        job.cancel()
    }

    /**
     * Returns the PDF page and local chunk for a global reader chunk index.
     * Unknown pages are treated as one chunk until their real count is ready.
     */
    suspend fun locationFor(globalChunk: Int): PageLocation {
        var remaining = globalChunk.coerceAtLeast(0)
        val count = totalPdfPages.value
        for (pageIndex in 0 until count) {
            val chunkCount = getChunkCount(pageIndex)
            if (remaining < chunkCount) {
                return PageLocation(pageIndex, remaining)
            }
            remaining -= chunkCount
        }
        return PageLocation(count - 1, 0)
    }

    /**
     * Returns the text for the global reader chunk index. Blank pages return
     * an empty string; the caller should render them as images.
     */
    suspend fun chunkText(globalChunk: Int): String {
        val location = locationFor(globalChunk)
        val chunks = cache.getChunks(uri, location.pdfPage, params)
        return chunks.getOrNull(location.chunk) ?: ""
    }

    /**
     * Returns true if the PDF page has no extractable text and should be shown
     * as a bitmap.
     */
    suspend fun isImagePage(pageIndex: Int): Boolean {
        val chunks = cache.getChunks(uri, pageIndex, params)
        return chunks.isEmpty()
    }

    /**
     * Returns the cached chunk count for a PDF page, computing it if needed.
     */
    suspend fun getChunkCount(pageIndex: Int): Int {
        synchronized(chunkCountsLock) {
            chunkCounts[pageIndex]?.let { return it }
        }
        val count = cache.getChunkCount(uri, pageIndex, params)
        setChunkCount(pageIndex, count)
        return count
    }

    private fun setChunkCount(pageIndex: Int, count: Int) {
        synchronized(chunkCountsLock) {
            chunkCounts[pageIndex] = count
            _totalChunks.value = computeTotal()
        }
    }

    private fun computeTotal(): Int {
        val count = totalPdfPages.value
        var total = 0
        for (i in 0 until count) {
            total += chunkCounts[i] ?: 1
        }
        return total
    }

    private suspend fun computeRemainingChunks() {
        withContext(Dispatchers.IO) {
            val count = totalPdfPages.value
            // Limit parallelism to avoid saturating the disk and PDFBox.
            val semaphore = Semaphore(PARALLELISM)
            val jobs = (0 until count).map { pageIndex ->
                async {
                    semaphore.withPermit {
                        getChunkCount(pageIndex)
                    }
                }
            }
            jobs.awaitAll()
            _isReady.value = true
            LogManager.d("PdfPaginator", "ready totalChunks=${_totalChunks.value} pdfPages=$count")
        }
    }

    data class PageLocation(val pdfPage: Int, val chunk: Int)

    companion object {
        private const val PARALLELISM = 4
    }
}
