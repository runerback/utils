package space.subread.dictionary.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Structured content of a glossary as a small block/inline tree, so a UI can
 *  render it natively (bullets, ruby, tag chips) instead of flattening it. */
sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Ruby(val base: List<Inline>, val reading: String) : Inline
    data class Link(val href: String, val text: String) : Inline

    /** A dictionary tag chip, e.g. the "noun" part-of-speech badge. */
    data class Tag(val label: String, val title: String?) : Inline
    data class Styled(val children: List<Inline>, val bold: Boolean = false, val italic: Boolean = false) : Inline

    companion object {
        /** Plain text of a subtree, e.g. for a link label or a test assertion. */
        fun plain(nodes: List<Inline>): String = nodes.joinToString("") {
            when (it) {
                is Text -> it.text
                is Ruby -> plain(it.base)
                is Link -> it.text
                is Tag -> it.label
                is Styled -> plain(it.children)
            }
        }
    }
}

sealed interface Block {
    data class Line(val children: List<Inline>) : Block
    data class Bullets(val items: List<List<Inline>>) : Block

    /** A visually grouped section, e.g. a Jitendex example-sentence box. */
    data class Box(val blocks: List<Block>) : Block
}

object Structure {

    fun parse(glossaryJson: String): List<Block> {
        val root = runCatching { JsonParser.parseString(glossaryJson) }.getOrNull()
        if (root == null || !root.isJsonArray) return listOf(Block.Line(listOf(Inline.Text(glossaryJson))))
        return root.asJsonArray.flatMap(::definition)
            .filter { it !is Block.Line || it.children.isNotEmpty() }
    }

    private fun definition(element: JsonElement): List<Block> = when {
        element.isJsonPrimitive -> listOf(Block.Line(listOf(Inline.Text(element.asString))))
        element.isJsonArray -> element.asJsonArray.flatMap(::definition)
        element.isJsonObject -> {
            val obj = element.asJsonObject
            when (obj.get("type")?.asString) {
                "text" -> listOf(Block.Line(listOf(Inline.Text(obj.str("text")))))
                "image" -> listOf(Block.Line(listOf(Inline.Text(imageAlt(obj)))))
                "structured-content" -> blocks(obj.get("content"))
                else -> blocks(element)
            }
        }
        else -> emptyList()
    }

    /** A node's children as blocks: runs of inline content become [Block.Line],
     *  lists and boxes stay structured. */
    private fun blocks(element: JsonElement?): List<Block> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonPrimitive) return listOf(Block.Line(listOf(Inline.Text(element.asString))))
        if (element.isJsonArray) return blocksOf(element.asJsonArray.toList())
        val obj = element.asJsonObject
        if (isBox(obj)) return listOf(Block.Box(blocks(obj.get("content"))))
        return when (obj.get("tag")?.asString) {
            "ul", "ol" -> listOf(bullets(obj))
            "img" -> listOf(Block.Line(listOf(Inline.Text(imageAlt(obj)))))
            "div", "details", "summary", "table", "thead", "tbody", "tfoot", "tr" ->
                blocks(obj.get("content"))
            // A leaf/inline node at block level gets its own line.
            else -> listOf(Block.Line(inlines(element)))
        }
    }

    /** Sibling nodes, keeping inline runs together on one line: "JMdict | Tatoeba"
     *  is one line, not three. Block nodes and <br> flush the current run. */
    private fun blocksOf(children: List<JsonElement>): List<Block> {
        val out = mutableListOf<Block>()
        val run = mutableListOf<Inline>()
        for (child in children) {
            val obj = child.takeIf { it.isJsonObject }?.asJsonObject
            when {
                obj == null -> run += inlines(child)
                obj.get("tag")?.asString == "br" -> flushLine(out, run)
                isBlock(obj) -> {
                    flushLine(out, run)
                    out += blocks(child)
                }
                else -> run += inlines(child)
            }
        }
        flushLine(out, run)
        return out
    }

    private fun flushLine(out: MutableList<Block>, run: MutableList<Inline>) {
        if (run.isNotEmpty()) {
            out += Block.Line(run.toList())
            run.clear()
        }
    }

    private fun isBlock(obj: JsonObject): Boolean =
        isBox(obj) || obj.get("tag")?.asString in setOf(
            "ul", "ol", "div", "details", "summary", "table", "thead", "tbody", "tfoot", "tr",
        )

    /** Jitendex marks the example-sentence box with a data class. */
    private fun isBox(obj: JsonObject): Boolean =
        obj.data("class").contains("extra-box") || obj.data("content") == "example-sentence"

    private fun bullets(ul: JsonObject): Block.Bullets {
        val content = ul.get("content") ?: return Block.Bullets(emptyList())
        val children = if (content.isJsonArray) content.asJsonArray.toList() else listOf(content)
        return Block.Bullets(children.map(::inlines).filter { it.isNotEmpty() })
    }

    private fun inlines(element: JsonElement?): List<Inline> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonPrimitive) return listOf(Inline.Text(element.asString))
        if (element.isJsonArray) return element.asJsonArray.flatMap(::inlines)
        val obj = element.asJsonObject
        when (obj.get("type")?.asString) {
            "text" -> return listOf(Inline.Text(obj.str("text")))
            "image" -> return listOf(Inline.Text(imageAlt(obj)))
            "structured-content" -> return inlines(obj.get("content"))
        }
        val content = obj.get("content")
        when (obj.get("tag")?.asString) {
            "br" -> return listOf(Inline.Text("\n"))
            "rp" -> return emptyList()
            // An rt outside a ruby keeps the reading inline, in parentheses.
            "rt" -> return listOf(Inline.Text("(" + plainText(content) + ")"))
            "ruby" -> {
                val children = content?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: return listOf(Inline.Text(plainText(content)))
                val base = children.filter { it.objTag() != "rt" && it.objTag() != "rp" }
                val reading = children.firstOrNull { it.objTag() == "rt" }
                    ?.let { plainText(it.asJsonObject.get("content")) }.orEmpty()
                return if (reading.isEmpty()) base.flatMap(::inlines)
                else listOf(Inline.Ruby(base.flatMap(::inlines), reading))
            }
            "img" -> return listOf(Inline.Text(imageAlt(obj)))
            "a" -> {
                val href = obj.get("href")?.asString
                return if (href != null && href.startsWith("http"))
                    listOf(Inline.Link(href, Inline.plain(inlines(content))))
                else inlines(content)
            }
        }
        var nodes = inlines(content)
        if (obj.data("class") == "tag") {
            return listOf(Inline.Tag(Inline.plain(nodes), obj.get("title")?.asString))
        }
        if (obj.data("content") == "example-keyword") {
            return listOf(Inline.Styled(nodes, bold = true))
        }
        val style = obj.get("style")?.takeIf { it.isJsonObject }?.asJsonObject
        if (style != null) {
            val bold = style.get("fontWeight")?.asString
                ?.let { it == "bold" || (it.toIntOrNull() ?: 0) >= 600 } == true
            val italic = style.get("fontStyle")?.asString == "italic"
            if (bold || italic) nodes = listOf(Inline.Styled(nodes, bold, italic))
        }
        return nodes
    }

    private fun plainText(element: JsonElement?): String = when {
        element == null || element.isJsonNull -> ""
        element.isJsonPrimitive -> element.asString
        element.isJsonArray -> element.asJsonArray.joinToString("") { plainText(it) }
        else -> {
            val obj = element.asJsonObject
            when {
                obj.get("type")?.asString == "text" -> obj.str("text")
                obj.get("tag")?.asString == "rp" -> ""
                else -> plainText(obj.get("content"))
            }
        }
    }

    /** The app has no image store. The alt text, or the file name, stands in for the picture. */
    private fun imageAlt(obj: JsonObject): String {
        val alt = obj.get("alt")?.asString ?: obj.get("title")?.asString
            ?: obj.get("path")?.asString?.substringAfterLast('/') ?: ""
        return "[$alt]"
    }

    private fun JsonObject.str(key: String): String = get(key)?.asString ?: ""

    private fun JsonObject.data(key: String): String =
        get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get(key)?.asString ?: ""

    private fun JsonElement.objTag(): String? =
        takeIf { isJsonObject }?.asJsonObject?.get("tag")?.asString
}
