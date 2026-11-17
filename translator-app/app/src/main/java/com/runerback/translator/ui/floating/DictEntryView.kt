@file:OptIn(ExperimentalLayoutApi::class)

package com.runerback.translator.ui.floating

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runerback.translator.dictionary.DictEntry
import space.subread.dictionary.core.Block
import space.subread.dictionary.core.Inline

private val Ink = Color.Black
private val Faint = Color.DarkGray
private val LinkBlue = Color(0xFF0645AD)

/** A dictionary entry rendered like the browser popup: bold headword, tag
 *  chips, bulleted senses, a boxed example sentence with furigana, links. */
@Composable
fun DictEntryView(entry: DictEntry, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(entry.expression, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            entry.reading?.let { Text(" ($it)", fontSize = 14.sp, color = Faint) }
            entry.reasons?.let { Text(" ← $it", fontSize = 12.sp, color = Faint) }
        }
        entry.metaLines.forEach { Text(it, fontSize = 12.sp, color = Faint) }
        entry.blocks.forEach { BlockNode(it) }
    }
}

@Composable
private fun BlockNode(block: Block) {
    when (block) {
        is Block.Line -> InlineFlow(block.children)
        is Block.Bullets -> Column {
            block.items.forEach { InlineFlow(listOf(Inline.Text("•  ")) + it) }
        }
        is Block.Box -> Column(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .padding(8.dp),
        ) {
            block.blocks.forEach { BlockNode(it) }
        }
    }
}

/** Inline content with wrapping between nodes, so furigana stays glued to its
 *  base characters. */
@Composable
private fun InlineFlow(nodes: List<Inline>) {
    FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        nodes.forEach { node ->
            Box(modifier = Modifier.align(Alignment.CenterVertically)) { InlineNode(node) }
        }
    }
}

@Composable
private fun InlineNode(node: Inline) {
    when (node) {
        is Inline.Text -> Text(node.text, fontSize = 16.sp, color = Ink)
        is Inline.Ruby -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(node.reading, fontSize = 9.sp, lineHeight = 9.sp, color = Faint, maxLines = 1)
            FlowRow {
                node.base.forEach { InlineNode(it) }
            }
        }
        is Inline.Link -> {
            val uriHandler = LocalUriHandler.current
            Text(
                node.text,
                fontSize = 14.sp,
                color = LinkBlue,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(node.href) } },
            )
        }
        is Inline.Tag -> Text(
            node.label,
            fontSize = 11.sp,
            color = Faint,
            modifier = Modifier
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        is Inline.Styled -> Text(
            Inline.plain(node.children),
            fontSize = 16.sp,
            color = Ink,
            fontWeight = if (node.bold) FontWeight.Bold else null,
            fontStyle = if (node.italic) FontStyle.Italic else null,
        )
    }
}
