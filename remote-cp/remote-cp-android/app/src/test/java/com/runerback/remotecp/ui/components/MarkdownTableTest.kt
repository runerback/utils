package com.runerback.remotecp.ui.components

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableTest {

    private fun printAst(node: Node, indent: String = "") {
        println("$indent${node.javaClass.simpleName}")
        var child = node.firstChild
        while (child != null) {
            printAst(child, "$indent  ")
            child = child.next
        }
    }

    @Test
    fun parseTable() {
        val markdown = """
            | A / E | B | C |
            | :--- | :--- | :--- |
            | a | b | c |
        """.trimIndent()

        val parser = Parser.builder()
            .extensions(listOf(TablesExtension.create()))
            .build()
        val document = parser.parse(markdown)

        println("=== AST ===")
        printAst(document)

        val table = document.firstChild
        check(table is TableBlock) { "Expected TableBlock, got ${table?.javaClass}" }

        val head = table.firstChild
        check(head is TableHead) { "Expected TableHead, got ${head?.javaClass}" }

        val body = head.next
        check(body is TableBody) { "Expected TableBody, got ${body?.javaClass}" }

        val headRow = head.firstChild
        check(headRow is TableRow) { "Expected TableRow in head, got ${headRow?.javaClass}" }

        val bodyRow = body.firstChild
        check(bodyRow is TableRow) { "Expected TableRow in body, got ${bodyRow?.javaClass}" }
    }

    @Test
    fun renderTable() {
        val markdown = """
            | A / E | B | C |
            | :--- | :--- | :--- |
            | a | b | c |
        """.trimIndent()

        val annotated = buildMarkdownAnnotatedString(markdown)
        val rendered = annotated.text
        println("=== Rendered ===")
        println(rendered)

        assertTrue("Header row missing", rendered.contains("A / E | B | C"))
        assertTrue("Separator row missing", rendered.contains("--- | --- | ---"))
        assertTrue("Body row missing", rendered.contains("a | b | c"))
    }

    @Test
    fun renderTableWithCrLf() {
        val markdown = "| A / E | B | C |\r\n| :--- | :--- | :--- |\r\n| a | b | c |"

        val annotated = buildMarkdownAnnotatedString(markdown)
        val rendered = annotated.text
        println("=== Rendered with CRLF ===")
        println(rendered)

        assertTrue("Header row missing", rendered.contains("A / E | B | C"))
        assertTrue("Separator row missing", rendered.contains("--- | --- | ---"))
        assertTrue("Body row missing", rendered.contains("a | b | c"))
    }
}
