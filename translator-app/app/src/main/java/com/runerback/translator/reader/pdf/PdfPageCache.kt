package com.runerback.translator.reader.pdf

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.runerback.translator.reader.text.computePages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caches screen-sized text chunks for individual PDF pages.
 *
 * The cache is keyed by URI, page index, and the rendering parameters that
 * affect pagination (font size, line height, container size, padding). A blank
 * page is represented by an empty chunk list; callers typically count such a
 * page as one image page.
 */
class PdfPageCache(context: Context) {

    private val appContext = context.applicationContext

    data class RenderParams(
        val fontSizeSp: Float,
        val lineHeight: Float,
        val width: Int,
        val height: Int,
        val paddingPx: Int,
    )

    private data class Key(
        val uriString: String,
        val pageIndex: Int,
        val params: RenderParams,
    )

    private val cache = LruCache<Key, List<String>>(MAX_SIZE)

    /**
     * Returns the text chunks for the requested PDF page, computing and caching
     * them if necessary. An empty list means the page has no extractable text
     * and should be treated as an image page.
     */
    suspend fun getChunks(
        uri: Uri,
        pageIndex: Int,
        params: RenderParams,
    ): List<String> = withContext(Dispatchers.IO) {
        val key = Key(uri.toString(), pageIndex, params)
        cache.get(key)?.let { return@withContext it }

        val extractor = PdfContentExtractor(appContext)
        val content = extractor.extractPage(uri, pageIndex)
        val text = content?.text.orEmpty()

        val chunks = if (text.isBlank()) {
            emptyList()
        } else {
            computePages(
                text = text,
                width = params.width,
                height = params.height,
                fontSizeSp = params.fontSizeSp,
                lineSpacingMultiplier = params.lineHeight,
                paddingPx = params.paddingPx,
            )
        }

        cache.put(key, chunks)
        chunks
    }

    /**
     * Returns the number of reader chunks for the page. Blank pages count as 1
     * (the image render of the whole PDF page).
     */
    suspend fun getChunkCount(
        uri: Uri,
        pageIndex: Int,
        params: RenderParams,
    ): Int {
        val chunks = getChunks(uri, pageIndex, params)
        return if (chunks.isEmpty()) 1 else chunks.size
    }

    /**
     * Invalidates cached entries. Passing null for a field clears all entries
     * matching the non-null fields.
     */
    fun invalidate(uri: Uri? = null, params: RenderParams? = null) {
        if (uri == null && params == null) {
            cache.evictAll()
            return
        }

        cache.snapshot().keys.filter { key ->
            (uri == null || key.uriString == uri.toString()) &&
                (params == null || key.params == params)
        }.forEach { cache.remove(it) }
    }

    companion object {
        private const val MAX_SIZE = 100
    }
}
