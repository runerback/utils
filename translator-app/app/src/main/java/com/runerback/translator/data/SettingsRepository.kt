package com.runerback.translator.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.runerback.translator.bookshelf.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val dataStore = context.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    val baseUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL
    }

    val model: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MODEL] ?: DEFAULT_MODEL
    }

    val temperature: Flow<Double> = dataStore.data.map { prefs ->
        prefs[KEY_TEMPERATURE] ?: DEFAULT_TEMPERATURE
    }

    val useFakeServer: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_FAKE_SERVER] ?: false
    }

    val rootFolderUri: Flow<Uri?> = dataStore.data.map { prefs ->
        prefs[KEY_ROOT_FOLDER_URI]?.let { Uri.parse(it) }
    }

    val books: Flow<List<Book>> = dataStore.data.map { prefs ->
        prefs[KEY_BOOKS]?.let { json.decodeFromString(it) } ?: emptyList()
    }

    suspend fun setBaseUrl(value: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = value
        }
    }

    suspend fun setModel(value: String) {
        dataStore.edit { prefs ->
            prefs[KEY_MODEL] = value
        }
    }

    suspend fun setTemperature(value: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_TEMPERATURE] = value
        }
    }

    suspend fun setUseFakeServer(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_USE_FAKE_SERVER] = value
        }
    }

    suspend fun setRootFolderUri(uri: Uri) {
        dataStore.edit { prefs ->
            prefs[KEY_ROOT_FOLDER_URI] = uri.toString()
        }
    }

    suspend fun setBooks(books: List<Book>) {
        dataStore.edit { prefs ->
            prefs[KEY_BOOKS] = json.encodeToString(books)
        }
    }

    suspend fun updateBookLastPage(bookId: String, page: Int) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOOKS]?.let { json.decodeFromString<List<Book>>(it) } ?: emptyList()
            val updated = current.map { book ->
                if (book.id == bookId) book.copy(lastPage = page) else book
            }
            prefs[KEY_BOOKS] = json.encodeToString(updated)
        }
    }

    suspend fun updateBookThumbnail(bookId: String, coverUri: Uri?, thumbnailPage: Int) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOOKS]?.let { json.decodeFromString<List<Book>>(it) } ?: emptyList()
            val updated = current.map { book ->
                if (book.id == bookId) book.copy(coverUri = coverUri, thumbnailPage = thumbnailPage) else book
            }
            prefs[KEY_BOOKS] = json.encodeToString(updated)
        }
    }

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("ollama_base_url")
        private val KEY_MODEL = stringPreferencesKey("ollama_model")
        private val KEY_TEMPERATURE = doublePreferencesKey("ollama_temperature")
        private val KEY_USE_FAKE_SERVER = booleanPreferencesKey("use_fake_server")
        private val KEY_ROOT_FOLDER_URI = stringPreferencesKey("root_folder_uri")
        private val KEY_BOOKS = stringPreferencesKey("books_json")

        const val DEFAULT_BASE_URL = "http://127.0.0.1:11434"
        const val DEFAULT_MODEL = "qwen3:14b"
        const val DEFAULT_TEMPERATURE = 0.2
    }
}
