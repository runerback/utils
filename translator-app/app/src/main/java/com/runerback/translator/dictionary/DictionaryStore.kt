package com.runerback.translator.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.sync.withLock
import space.subread.dictionary.core.DictionarySink
import space.subread.dictionary.core.StoredTerm
import space.subread.dictionary.core.Tag
import space.subread.dictionary.core.Term
import space.subread.dictionary.core.TermMeta
import space.subread.dictionary.core.TermSource

/** One imported dictionary. `terms` counts the term rows and the meta rows. */
data class DictionaryInfo(val id: Long, val title: String, val revision: String, val position: Int, val enabled: Boolean, val terms: Int)

/** A frequency or pitch entry, with the title of the dictionary it comes from. */
data class StoredMeta(val dictionaryTitle: String, val meta: TermMeta)

/**
 * The imported dictionaries, in one SQLite database created empty on first launch.
 * Dictionaries are imported from Yomitan zips in settings (see `DictionaryImporter`).
 */
class DictionaryStore internal constructor(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION), DictionaryLookupSource {

    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE dictionaries (id INTEGER PRIMARY KEY, title TEXT NOT NULL UNIQUE, revision TEXT NOT NULL, position INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1)")
        db.execSQL(
            "CREATE TABLE terms (id INTEGER PRIMARY KEY, dictionary INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, " +
                "definition_tags TEXT NOT NULL, rules TEXT NOT NULL, score REAL NOT NULL, glossary TEXT NOT NULL, sequence INTEGER NOT NULL, term_tags TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX terms_expression ON terms(expression)")
        db.execSQL("CREATE INDEX terms_reading ON terms(reading)")
        db.execSQL("CREATE INDEX terms_dictionary ON terms(dictionary)")
        db.execSQL("CREATE TABLE meta (id INTEGER PRIMARY KEY, dictionary INTEGER NOT NULL, expression TEXT NOT NULL, mode TEXT NOT NULL, data TEXT NOT NULL)")
        db.execSQL("CREATE INDEX meta_expression ON meta(expression)")
        db.execSQL("CREATE INDEX meta_dictionary ON meta(dictionary)")
    }

    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun list(): List<DictionaryInfo> = readableDatabase.rawQuery(
        "SELECT d.id, d.title, d.revision, d.position, d.enabled, " +
            "(SELECT COUNT(*) FROM terms t WHERE t.dictionary = d.id) + (SELECT COUNT(*) FROM meta m WHERE m.dictionary = d.id) " +
            "FROM dictionaries d ORDER BY d.position",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(DictionaryInfo(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4) != 0, c.getInt(5)))
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        writableDatabase.execSQL("UPDATE dictionaries SET enabled = ? WHERE id = ?", arrayOf<Any>(if (enabled) 1 else 0, id))
    }

    /** Deletes a dictionary with all its term and meta rows. */
    fun deleteDictionary(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            deleteDictionaryRows(db, id)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Inserts one dictionary row and returns its id. Re-importing a title replaces the old
     * dictionary in place: its rows are deleted first and its position is kept. Call inside
     * a transaction together with the row inserts, so a failed import leaves nothing behind.
     */
    fun insertDictionary(db: SQLiteDatabase, title: String, revision: String): Long {
        val existing = db.rawQuery("SELECT id, position FROM dictionaries WHERE title = ?", arrayOf(title)).use { c ->
            if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else null
        }
        val position = if (existing != null) {
            deleteDictionaryRows(db, existing.first)
            existing.second
        } else {
            db.rawQuery("SELECT COALESCE(MAX(position), 0) + 1 FROM dictionaries", null).use { c ->
                c.moveToFirst()
                c.getInt(0)
            }
        }
        val insert = db.compileStatement("INSERT INTO dictionaries (title, revision, position, enabled) VALUES (?, ?, ?, 1)")
        insert.bindString(1, title)
        insert.bindString(2, revision)
        insert.bindLong(3, position.toLong())
        return insert.executeInsert()
    }

    /** Feeds `YomitanZip.readBanks` rows into compiled inserts. Call inside a transaction. */
    fun importSink(db: SQLiteDatabase, dictionaryId: Long): DictionarySink {
        val termInsert = db.compileStatement(
            "INSERT INTO terms (dictionary, expression, reading, definition_tags, rules, score, glossary, sequence, term_tags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        val metaInsert = db.compileStatement("INSERT INTO meta (dictionary, expression, mode, data) VALUES (?, ?, ?, ?)")
        return object : DictionarySink {
            override fun term(term: Term) {
                termInsert.clearBindings()
                termInsert.bindLong(1, dictionaryId)
                termInsert.bindString(2, term.expression)
                // A term with no reading is read as it is written. The stored reading is the text a kana lookup finds.
                termInsert.bindString(3, term.reading.ifEmpty { term.expression })
                termInsert.bindString(4, term.definitionTags)
                termInsert.bindString(5, term.rules)
                termInsert.bindDouble(6, term.score)
                termInsert.bindString(7, term.glossary)
                termInsert.bindLong(8, term.sequence)
                termInsert.bindString(9, term.termTags)
                termInsert.executeInsert()
            }

            override fun termMeta(meta: TermMeta) {
                metaInsert.clearBindings()
                metaInsert.bindLong(1, dictionaryId)
                metaInsert.bindString(2, meta.expression)
                metaInsert.bindString(3, meta.mode)
                metaInsert.bindString(4, meta.data)
                metaInsert.executeInsert()
            }

            // The tag bank gives the notes of a tag. The pop-up shows the tag names alone.
            override fun tag(tag: Tag) = Unit
        }
    }

    private fun deleteDictionaryRows(db: SQLiteDatabase, id: Long) {
        db.execSQL("DELETE FROM terms WHERE dictionary = ?", arrayOf<Any>(id))
        db.execSQL("DELETE FROM meta WHERE dictionary = ?", arrayOf<Any>(id))
        db.execSQL("DELETE FROM dictionaries WHERE id = ?", arrayOf<Any>(id))
    }

    override fun anyEnabled(): Boolean = readableDatabase.rawQuery("SELECT 1 FROM dictionaries WHERE enabled = 1", null).use { it.moveToFirst() }

    override fun terms(texts: Collection<String>): List<StoredTerm> {
        val db = readableDatabase
        val out = ArrayList<StoredTerm>()
        // SQLite takes a limited number of parameters in one statement.
        for (chunk in texts.chunked(400)) {
            val marks = chunk.joinToString(",") { "?" }
            val args = (chunk + chunk).toTypedArray()
            db.rawQuery(
                "SELECT t.expression, t.reading, t.definition_tags, t.rules, t.score, t.glossary, t.sequence, t.term_tags, d.id, d.title, d.position " +
                    "FROM terms t JOIN dictionaries d ON d.id = t.dictionary " +
                    "WHERE d.enabled = 1 AND (t.expression IN ($marks) OR t.reading IN ($marks))",
                args,
            ).use { c ->
                while (c.moveToNext()) {
                    val term = Term(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getString(5), c.getLong(6), c.getString(7))
                    out.add(StoredTerm(c.getLong(8), c.getString(9), c.getInt(10), term))
                }
            }
        }
        return out
    }

    /** The frequency and pitch entries of an expression, from the enabled dictionaries in their order. */
    override fun meta(expression: String): List<StoredMeta> = readableDatabase.rawQuery(
        "SELECT m.expression, m.mode, m.data, d.title FROM meta m JOIN dictionaries d ON d.id = m.dictionary " +
            "WHERE d.enabled = 1 AND m.expression = ? ORDER BY d.position",
        arrayOf(expression),
    ).use { c ->
        buildList { while (c.moveToNext()) add(StoredMeta(c.getString(3), TermMeta(c.getString(0), c.getString(1), c.getString(2)))) }
    }

    companion object {
        const val NAME = "dictionaries.db"
        const val VERSION = 1
    }
}

object DictionaryStoreProvider {

    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var instance: DictionaryStore? = null

    /** The shared store. The database starts empty; dictionaries are imported from Yomitan zips in settings. */
    suspend fun get(context: Context): DictionaryStore {
        instance?.let { return it }
        return mutex.withLock {
            instance?.let { return@withLock it }
            DictionaryStore(context.applicationContext).also { instance = it }
        }
    }
}
