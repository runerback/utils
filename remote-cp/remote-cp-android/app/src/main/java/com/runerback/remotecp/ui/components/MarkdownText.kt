package com.runerback.remotecp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser

private const val URL_ANNOTATION_TAG = "URL"

private sealed class MarkdownBlock {
    data class Paragraph(val text: AnnotatedString) : MarkdownBlock()
    data class Heading(val text: AnnotatedString) : MarkdownBlock()
    data class Code(val text: String) : MarkdownBlock()
    data class MarkdownList(val items: List<AnnotatedString>, val ordered: Boolean, val startNumber: Int) : MarkdownBlock()
    data class Table(
        val rows: List<List<AnnotatedString>>,
        val alignments: List<TableCell.Alignment>,
        val hasBody: Boolean
    ) : MarkdownBlock()
}

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            when (block) {
                is MarkdownBlock.Paragraph -> Text(
                    text = block.text,
                    color = color,
                    style = style
                )
                is MarkdownBlock.Heading -> Text(
                    text = block.text,
                    color = color,
                    style = style.copy(fontWeight = FontWeight.Bold)
                )
                is MarkdownBlock.Code -> {
                    val annotated = remember(block.text) {
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = Color(0xFF334155)
                                )
                            ) {
                                append(block.text)
                            }
                        }
                    }
                    Text(
                        text = annotated,
                        color = color,
                        style = style.copy(fontFamily = FontFamily.Monospace)
                    )
                }
                is MarkdownBlock.MarkdownList -> MarkdownList(block, color, style)
                is MarkdownBlock.Table -> MarkdownTable(block, color, style)
            }
        }
    }
}

@Composable
private fun MarkdownList(block: MarkdownBlock.MarkdownList, color: Color, style: TextStyle) {
    Column {
        block.items.forEachIndexed { index, item ->
            val prefix = if (block.ordered) "${block.startNumber + index}. " else "• "
            val annotated = remember(prefix, item) {
                buildAnnotatedString {
                    append(prefix)
                    append(item)
                }
            }
            Text(text = annotated, color = color, style = style)
        }
    }
}

@Composable
private fun MarkdownTable(block: MarkdownBlock.Table, color: Color, style: TextStyle) {
    val dividerColor = Color(0xFF64748b)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF334155).copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        block.rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { cellIndex, cell ->
                    if (cellIndex > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(dividerColor)
                        )
                    }
                    val alignment = block.alignments.getOrElse(cellIndex) { TableCell.Alignment.LEFT }
                    val textAlign = when (alignment) {
                        TableCell.Alignment.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
                        TableCell.Alignment.RIGHT -> androidx.compose.ui.text.style.TextAlign.Right
                        else -> androidx.compose.ui.text.style.TextAlign.Start
                    }
                    Text(
                        text = cell,
                        color = color,
                        style = if (rowIndex == 0 && !block.hasBody) {
                            style.copy(fontWeight = FontWeight.Bold, textAlign = textAlign)
                        } else if (rowIndex == 0) {
                            style.copy(fontWeight = FontWeight.Bold, textAlign = textAlign)
                        } else {
                            style.copy(textAlign = textAlign)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()
    val document = parser.parse(markdown)
    val blocks = mutableListOf<MarkdownBlock>()
    var child = document.firstChild
    while (child != null) {
        when (child) {
            is Paragraph -> blocks.add(MarkdownBlock.Paragraph(buildInlineAnnotatedString(child)))
            is Heading -> blocks.add(MarkdownBlock.Heading(buildInlineAnnotatedString(child)))
            is FencedCodeBlock -> blocks.add(MarkdownBlock.Code(child.literal))
            is IndentedCodeBlock -> blocks.add(MarkdownBlock.Code(child.literal))
            is ListBlock -> blocks.add(parseList(child))
            is TableBlock -> blocks.add(parseTable(child))
        }
        child = child.next
    }
    return blocks
}

private fun parseList(list: ListBlock): MarkdownBlock.MarkdownList {
    val items = mutableListOf<AnnotatedString>()
    var item = list.firstChild
    while (item != null) {
        if (item is ListItem) {
            items.add(buildListItemAnnotatedString(item))
        }
        item = item.next
    }
    val ordered = list is OrderedList
    val startNumber = (list as? OrderedList)?.startNumber ?: 1
    return MarkdownBlock.MarkdownList(items, ordered, startNumber)
}

private fun parseTable(table: TableBlock): MarkdownBlock.Table {
    val rows = mutableListOf<List<AnnotatedString>>()
    val alignments = mutableListOf<TableCell.Alignment>()
    var hasBody = false
    var section = table.firstChild
    while (section != null) {
        when (section) {
            is TableBody -> hasBody = true
        }
        var row = section.firstChild
        while (row != null) {
            if (row is TableRow) {
                val cells = mutableListOf<AnnotatedString>()
                var cell = row.firstChild
                while (cell != null) {
                    if (cell is TableCell) {
                        if (alignments.size < cells.size + 1) {
                            alignments.add(cell.alignment ?: TableCell.Alignment.LEFT)
                        }
                        cells.add(buildInlineAnnotatedString(cell))
                    }
                    cell = cell.next
                }
                rows.add(cells)
            }
            row = row.next
        }
        section = section.next
    }
    return MarkdownBlock.Table(rows, alignments, hasBody)
}

private fun buildInlineAnnotatedString(node: Node): AnnotatedString {
    return buildAnnotatedString {
        renderInlineChildren(node)
    }
}

private fun buildListItemAnnotatedString(item: ListItem): AnnotatedString {
    return buildAnnotatedString {
        var child = item.firstChild
        while (child != null) {
            when (child) {
                is Paragraph -> renderInlineChildren(child)
                is ListBlock -> {
                    append("\n")
                    renderListContent(child)
                }
                else -> renderBlockChildren(child)
            }
            child = child.next
        }
    }
}

private fun AnnotatedString.Builder.renderBlockChildren(node: Node) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is Paragraph -> renderInlineChildren(child)
            is Heading -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    renderInlineChildren(child)
                }
            }
            is FencedCodeBlock -> {
                val literal = child.literal
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF334155)
                    )
                ) {
                    append(literal)
                }
            }
            is IndentedCodeBlock -> {
                val literal = child.literal
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF334155)
                    )
                ) {
                    append(literal)
                }
            }
            is ListBlock -> renderListContent(child)
            is TableBlock -> renderTableContent(child)
        }
        child = child.next
    }
}

private fun AnnotatedString.Builder.renderListContent(list: ListBlock) {
    var item = list.firstChild
    var index = 1
    while (item != null) {
        if (item is ListItem) {
            val prefix = when (list) {
                is BulletList -> "• "
                is OrderedList -> "${index++}. "
                else -> ""
            }
            append(prefix)
            var child = item.firstChild
            while (child != null) {
                when (child) {
                    is Paragraph -> renderInlineChildren(child)
                    is ListBlock -> {
                        append("\n")
                        renderListContent(child)
                    }
                    else -> renderBlockChildren(child)
                }
                child = child.next
            }
            if (item.next != null) append("\n")
        }
        item = item.next
    }
}

private fun AnnotatedString.Builder.renderTableContent(table: TableBlock) {
    var section = table.firstChild
    while (section != null) {
        var row = section.firstChild
        while (row != null) {
            if (row is TableRow) {
                var cell = row.firstChild
                var first = true
                while (cell != null) {
                    if (cell is TableCell) {
                        if (!first) append(" | ")
                        first = false
                        renderInlineChildren(cell)
                    }
                    cell = cell.next
                }
                if (row.next != null || section.next != null) append("\n")
            }
            row = row.next
        }
        section = section.next
    }
}

private fun AnnotatedString.Builder.renderInlineChildren(node: Node) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is Text -> append(child.literal)
            is Emphasis -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    renderInlineChildren(child)
                }
            }
            is StrongEmphasis -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    renderInlineChildren(child)
                }
            }
            is Code -> {
                val literal = child.literal
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF334155)
                    )
                ) {
                    append(literal)
                }
            }
            is Link -> {
                val start = length
                withStyle(
                    SpanStyle(
                        color = Color(0xFF38bdf8),
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    renderInlineChildren(child)
                }
                addStringAnnotation(
                    tag = URL_ANNOTATION_TAG,
                    annotation = child.destination,
                    start = start,
                    end = length
                )
            }
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            else -> renderInlineChildren(child)
        }
        child = child.next
    }
}

internal fun buildMarkdownAnnotatedString(markdown: String): AnnotatedString {
    val blocks = parseMarkdownBlocks(markdown)
    return buildAnnotatedString {
        blocks.forEachIndexed { index, block ->
            if (index > 0) append("\n\n")
            when (block) {
                is MarkdownBlock.Paragraph -> append(block.text)
                is MarkdownBlock.Heading -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(block.text)
                    }
                }
                is MarkdownBlock.Code -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF334155)
                        )
                    ) {
                        append(block.text)
                    }
                }
                is MarkdownBlock.MarkdownList -> {
                    block.items.forEachIndexed { i, item ->
                        if (i > 0) append("\n")
                        val prefix = if (block.ordered) "${block.startNumber + i}. " else "• "
                        append(prefix)
                        append(item)
                    }
                }
                is MarkdownBlock.Table -> {
                    block.rows.forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) append("\n")
                        if (rowIndex == 1 && block.hasBody) {
                            block.rows[0].forEachIndexed { cellIndex, _ ->
                                if (cellIndex > 0) append(" | ")
                                append("---")
                            }
                            append("\n")
                        }
                        row.forEachIndexed { cellIndex, cell ->
                            if (cellIndex > 0) append(" | ")
                            append(cell)
                        }
                    }
                }
            }
        }
    }
}
