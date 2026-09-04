package com.runerback.translator.reader.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object HtmlCleaner {

    fun toPlainText(html: String): String {
        val document = Jsoup.parse(html)
        val body = document.body()
        val builder = StringBuilder()
        walk(body, builder)
        return builder.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    private fun walk(node: Node, builder: StringBuilder) {
        when (node) {
            is TextNode -> {
                val text = node.text().replace(Regex("\\s+"), " ")
                if (text.isNotBlank()) {
                    if (builder.isNotEmpty() && builder.last() != ' ' && builder.last() != '\n' && !text.startsWith(" ")) {
                        builder.append(" ")
                    }
                    builder.append(text)
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                when (tag) {
                    "br" -> builder.append("\n\n")
                    "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li" -> {
                        node.childNodes().forEach { walk(it, builder) }
                        builder.append("\n\n")
                    }
                    else -> node.childNodes().forEach { walk(it, builder) }
                }
            }
        }
    }
}
