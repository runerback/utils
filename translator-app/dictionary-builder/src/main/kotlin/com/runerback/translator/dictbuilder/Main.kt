package com.runerback.translator.dictbuilder

import space.subread.dictionary.core.DictionarySink
import space.subread.dictionary.core.Tag
import space.subread.dictionary.core.Term
import space.subread.dictionary.core.TermMeta
import space.subread.dictionary.core.YomitanZip
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Builds `dictionaries.db` from every Yomitan format-3 zip in a folder.
 *
 * Usage: `<dictionaries dir> <output db>`. The schema matches the runtime dictionary store in the
 * app (`dictionary/DictionaryStore.kt`): keep both in sync when one changes.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: dictionary-builder <dictionaries dir> <output db>" }
    val dir = File(args[0])
    val out = File(args[1])
    val zips = dir.listFiles { file -> file.extension.lowercase() == "zip" }.orEmpty().sortedBy { it.name }
    check(zips.isNotEmpty()) { "No dictionary zips in $dir" }

    out.parentFile?.mkdirs()
    out.delete() // rebuild from scratch: the bundled set is defined by the folder alone

    DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { conn ->
        conn.autoCommit = false
        createSchema(conn)
        var position = 0
        for (zip in zips) {
            val index = zip.inputStream().buffered().use { YomitanZip.readIndex(it) }
            if (index.format != 3) {
                println("SKIP ${zip.name}: format ${index.format}, only 3 is supported")
                continue
            }
            position++
            val id = insertDictionary(conn, index.title, index.revision, position)
            var count = 0
            zip.inputStream().buffered().use { input ->
                YomitanZip.readBanks(input, bankSink(conn, id) { count += it })
            }
            println("OK ${index.title} ${index.revision}: $count rows")
        }
        conn.commit()
    }
    println("Wrote $out")
}

// Same tables as space.subread.dictionary.Dictionaries (upstream) and the app's DictionaryStore.
private fun createSchema(conn: Connection) {
    conn.createStatement().use { st ->
        st.executeUpdate("CREATE TABLE dictionaries (id INTEGER PRIMARY KEY, title TEXT NOT NULL UNIQUE, revision TEXT NOT NULL, position INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1)")
        st.executeUpdate(
            "CREATE TABLE terms (id INTEGER PRIMARY KEY, dictionary INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, " +
                "definition_tags TEXT NOT NULL, rules TEXT NOT NULL, score REAL NOT NULL, glossary TEXT NOT NULL, sequence INTEGER NOT NULL, term_tags TEXT NOT NULL)",
        )
        st.executeUpdate("CREATE INDEX terms_expression ON terms(expression)")
        st.executeUpdate("CREATE INDEX terms_reading ON terms(reading)")
        st.executeUpdate("CREATE INDEX terms_dictionary ON terms(dictionary)")
        st.executeUpdate("CREATE TABLE meta (id INTEGER PRIMARY KEY, dictionary INTEGER NOT NULL, expression TEXT NOT NULL, mode TEXT NOT NULL, data TEXT NOT NULL)")
        st.executeUpdate("CREATE INDEX meta_expression ON meta(expression)")
        st.executeUpdate("CREATE INDEX meta_dictionary ON meta(dictionary)")
    }
}

private fun insertDictionary(conn: Connection, title: String, revision: String, position: Int): Long =
    conn.prepareStatement("INSERT INTO dictionaries (title, revision, position, enabled) VALUES (?, ?, ?, 1)").use { st ->
        st.setString(1, title)
        st.setString(2, revision)
        st.setInt(3, position)
        st.executeUpdate()
        conn.createStatement().use { it.executeQuery("SELECT last_insert_rowid()").run { next(); getLong(1) } }
    }

private fun bankSink(conn: Connection, dictionaryId: Long, progress: (Int) -> Unit): DictionarySink {
    val termInsert = conn.prepareStatement(
        "INSERT INTO terms (dictionary, expression, reading, definition_tags, rules, score, glossary, sequence, term_tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
    )
    val metaInsert = conn.prepareStatement("INSERT INTO meta (dictionary, expression, mode, data) VALUES (?, ?, ?, ?)")
    return object : DictionarySink {
        override fun term(term: Term) {
            termInsert.setLong(1, dictionaryId)
            termInsert.setString(2, term.expression)
            // A term with no reading is read as it is written. The stored reading is the text a kana lookup finds.
            termInsert.setString(3, term.reading.ifEmpty { term.expression })
            termInsert.setString(4, term.definitionTags)
            termInsert.setString(5, term.rules)
            termInsert.setDouble(6, term.score)
            termInsert.setString(7, term.glossary)
            termInsert.setLong(8, term.sequence)
            termInsert.setString(9, term.termTags)
            termInsert.executeUpdate()
            progress(1)
        }

        override fun termMeta(meta: TermMeta) {
            metaInsert.setLong(1, dictionaryId)
            metaInsert.setString(2, meta.expression)
            metaInsert.setString(3, meta.mode)
            metaInsert.setString(4, meta.data)
            metaInsert.executeUpdate()
            progress(1)
        }

        // The tag bank gives the notes of a tag. The pop-up shows the tag names alone.
        override fun tag(tag: Tag) = Unit
    }
}
