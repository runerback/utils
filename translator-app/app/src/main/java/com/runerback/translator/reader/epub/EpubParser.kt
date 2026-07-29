package com.runerback.translator.reader.epub

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipInputStream

class EpubParser(private val context: Context) {

    suspend fun parse(uri: Uri): Result<EpubBook> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = readZip(uri)
            val containerXml = zip["META-INF/container.xml"]
                ?: throw IllegalArgumentException("META-INF/container.xml not found")
            val opfPath = parseRootFilePath(containerXml)
                ?: throw IllegalArgumentException("Rootfile path not found")

            val opfXml = zip[opfPath]
                ?: throw IllegalArgumentException("OPF file not found: $opfPath")
            val opfDir = opfPath.substringBeforeLast("/", "")

            val (title, author) = parseMetadata(opfXml)
            val manifest = parseManifest(opfXml)
            val spineIds = parseSpine(opfXml)

            val chapters = spineIds.mapNotNull { id ->
                val item = manifest[id] ?: return@mapNotNull null
                val href = item.href
                val chapterPath = if (opfDir.isEmpty()) href else "$opfDir/$href"
                val rawHtml = zip[chapterPath] ?: return@mapNotNull null
                val body = HtmlCleaner.toPlainText(rawHtml)
                if (body.isBlank()) return@mapNotNull null
                EpubChapter(
                    id = id,
                    href = href,
                    title = item.title ?: href,
                    body = body,
                )
            }

            EpubBook(
                title = title ?: "Unknown",
                author = author,
                chapters = chapters,
            )
        }
    }

    private fun readZip(uri: Uri): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return entries
    }

    private fun parseRootFilePath(containerXml: String): String? {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(containerXml.reader())
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val path = parser.getAttributeValue(null, "full-path")
                if (path != null) return path
            }
            parser.next()
        }
        return null
    }

    private fun parseMetadata(opfXml: String): Pair<String?, String?> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(opfXml.reader())
        var title: String? = null
        var author: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "dc:title" -> title = parser.nextText()
                    "dc:creator" -> author = parser.nextText()
                }
            }
            parser.next()
        }
        return title to author
    }

    private fun parseManifest(opfXml: String): Map<String, ManifestItem> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(opfXml.reader())
        val items = mutableMapOf<String, ManifestItem>()
        var inManifest = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "manifest" -> inManifest = true
                        "item" -> {
                            if (inManifest) {
                                val id = parser.getAttributeValue(null, "id") ?: ""
                                val href = parser.getAttributeValue(null, "href") ?: ""
                                if (id.isNotBlank() && href.isNotBlank()) {
                                    items[id] = ManifestItem(id = id, href = href)
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "manifest") inManifest = false
                }
            }
            parser.next()
        }
        return items
    }

    private fun parseSpine(opfXml: String): List<String> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(opfXml.reader())
        val ids = mutableListOf<String>()
        var inSpine = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "spine" -> inSpine = true
                        "itemref" -> {
                            if (inSpine) {
                                parser.getAttributeValue(null, "idref")?.let { ids.add(it) }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "spine") inSpine = false
                }
            }
            parser.next()
        }
        return ids
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val title: String? = null,
    )
}
