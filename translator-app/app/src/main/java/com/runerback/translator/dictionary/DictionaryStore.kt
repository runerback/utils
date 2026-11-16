package com.runerback.translator.dictionary

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.edit
import kotlinx.coroutines.sync.withLock
import space.subread.dictionary.core.StoredTerm
import space.subread.dictionary.core.Term
import space.subread.dictionary.core.TermMeta
import space.subread.dictionary.core.TermSource
import java.io.IOException

/** One bundled dictionary. `terms` counts the term rows and the meta rows. */
data class DictionaryInfo(val id: Long, val title: String, val revision: String, val position: Int, val enabled: Boolean, val terms: Int)

/** A frequency or pitch entry, with the title of the dictionary it comes from. */
data class StoredMeta(val dictionaryTitle: String, val meta: TermMeta)

/**
 * The bundled dictionaries, in one SQLite database prebuilt at build time by
 * `:dictionary-builder` (same schema — keep both in sync) and shipped as an asset.
 * Read-only lookups plus an enabled toggle; the bundled set itself never changes at runtime.
 */
class DictionaryStore internal constructor(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION), DictionaryLookupSource {

    // The database file is copied from assets before the store opens; it is never created here.
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) = Unit
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

    private const val ASSET = "dictionaries.db"
    private const val PREFS = "dictionary"
    private const val KEY_COPIED_VERSION = "bundled_db_version_code"

    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var instance: DictionaryStore? = null

    /**
     * Returns the shared store, or null when this build has no bundled dictionary.
     * On first launch (and after an app update, which may carry a newer bundle) the
     * prebuilt database is copied out of the assets before the store opens.
     */
    suspend fun get(context: Context): DictionaryStore? {
        instance?.let { return it }
        return mutex.withLock {
            instance?.let { return@withLock it }
            val appContext = context.applicationContext
            if (!copyBundledDatabase(appContext)) return@withLock null
            DictionaryStore(appContext).also { instance = it }
        }
    }

    private fun copyBundledDatabase(context: Context): Boolean {
        val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dbFile = context.getDatabasePath(DictionaryStore.NAME)
        if (dbFile.exists() && prefs.getLong(KEY_COPIED_VERSION, -1) == versionCode) return true

        val input = try {
            context.assets.open(ASSET)
        } catch (e: IOException) {
            return false // no dictionary bundled in this build
        }
        dbFile.parentFile?.mkdirs()
        val tmp = java.io.File(dbFile.parentFile, "${DictionaryStore.NAME}.tmp")
        input.use { tmp.outputStream().use { out -> it.copyTo(out) } }
        if (instance == null && dbFile.exists()) dbFile.delete()
        if (!tmp.renameTo(dbFile)) {
            tmp.delete()
            return false
        }
        prefs.edit { putLong(KEY_COPIED_VERSION, versionCode) }
        return true
    }
}
