package com.runerback.tagem.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runerback.tagem.data.ImageStore
import com.runerback.tagem.data.MediaTagCrossRef
import com.runerback.tagem.data.TagDao
import com.runerback.tagem.data.TagDatabase
import com.runerback.tagem.data.TagEntity
import com.runerback.tagem.data.TaggedMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    application: Application,
    private val tagDao: TagDao,
) : AndroidViewModel(application) {

    data class UiState(
        val images: List<ImageStore.ImageItem> = emptyList(),
        val filteredImages: List<ImageStore.ImageItem> = emptyList(),
        val tags: List<TagEntity> = emptyList(),
        val selectedTagId: Long? = null,
        val searchQuery: String = "",
        val tagPanelOpen: Boolean = false,
        val selectedImageUri: Uri? = null,
        val selectedImageTags: List<TagEntity> = emptyList(),
        val isLoading: Boolean = false,
        val showGifsOnly: Boolean = false,
        val tagCounts: Map<String, Int> = emptyMap(),
        val isExporting: Boolean = false,
        val isImporting: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _allImages = MutableStateFlow<List<ImageStore.ImageItem>>(emptyList())
    private val _selectedTagId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _tagPanelOpen = MutableStateFlow(false)
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    private val _showGifsOnly = MutableStateFlow(false)
    private val _isExporting = MutableStateFlow(false)
    private val _isImporting = MutableStateFlow(false)

    private data class ImageFilterResult(
        val allImages: List<ImageStore.ImageItem>,
        val selectedTagId: Long?,
        val showGifsOnly: Boolean,
        val filteredImages: List<ImageStore.ImageItem>,
    )

    private data class CombinedState(
        val imageFilter: ImageFilterResult,
        val tags: List<TagEntity>,
        val selectedImageTags: List<TagEntity>,
        val tagPanelOpen: Boolean,
        val tagCounts: Map<String, Int>,
    )

    private val imageFilterFlow = combine(
        _allImages,
        _selectedTagId,
        _showGifsOnly,
    ) { allImages, selectedTagId, showGifsOnly ->
        val gifFiltered = if (showGifsOnly) {
            allImages.filter { it.isGif }
        } else {
            allImages
        }
        val filteredImages = if (selectedTagId != null) {
            val taggedUris = tagDao.getMediaUrisForTag(selectedTagId).first().toSet()
            gifFiltered.filter { it.uri.toString() in taggedUris }
        } else {
            gifFiltered
        }
        ImageFilterResult(allImages, selectedTagId, showGifsOnly, filteredImages)
    }

    private val tagsFlow = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            tagDao.getAllTags()
        } else {
            tagDao.searchTags(query)
        }
    }

    private val selectedImageTagsFlow = _selectedImageUri.flatMapLatest { uri ->
        if (uri != null) {
            tagDao.getTagsForMedia(uri.toString())
        } else {
            flowOf(emptyList())
        }
    }

    private val tagCountsFlow = tagDao.getTagCounts().map { counts ->
        counts.associate { it.mediaUri to it.count }
    }

    private val combinedStateFlow = combine(
        imageFilterFlow,
        tagsFlow,
        selectedImageTagsFlow,
        _tagPanelOpen,
        tagCountsFlow,
    ) { imageFilter, tags, selectedImageTags, tagPanelOpen, tagCounts ->
        CombinedState(imageFilter, tags, selectedImageTags, tagPanelOpen, tagCounts)
    }

    init {
        viewModelScope.launch {
            combine(
                combinedStateFlow,
                _isExporting,
                _isImporting,
            ) { combined, isExporting, isImporting ->
                UiState(
                    images = combined.imageFilter.allImages,
                    filteredImages = combined.imageFilter.filteredImages,
                    tags = combined.tags,
                    selectedTagId = combined.imageFilter.selectedTagId,
                    searchQuery = _searchQuery.value,
                    tagPanelOpen = combined.tagPanelOpen,
                    selectedImageUri = _selectedImageUri.value,
                    selectedImageTags = combined.selectedImageTags,
                    showGifsOnly = combined.imageFilter.showGifsOnly,
                    tagCounts = combined.tagCounts,
                    isExporting = isExporting,
                    isImporting = isImporting,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun loadImages() {
        viewModelScope.launch(Dispatchers.IO) {
            _allImages.value = ImageStore.listImages(getApplication())
        }
    }

    fun selectTag(tagId: Long?) {
        _selectedTagId.value = tagId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleTagPanel() {
        _tagPanelOpen.value = !_tagPanelOpen.value
    }

    fun toggleShowGifsOnly() {
        _showGifsOnly.value = !_showGifsOnly.value
    }

    fun selectImage(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun dismissEditor() {
        _selectedImageUri.value = null
    }

    fun addTagToImage(tagName: String) {
        val uri = _selectedImageUri.value ?: return
        val trimmed = tagName.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val tagId = tagDao.insertTag(TagEntity(name = trimmed))
            val finalTagId = if (tagId == -1L) {
                tagDao.searchTags(trimmed).first().firstOrNull { it.name == trimmed }?.id ?: return@launch
            } else {
                tagId
            }

            tagDao.insertTaggedMedia(TaggedMediaEntity(mediaUri = uri.toString()))
            tagDao.insertCrossRef(MediaTagCrossRef(mediaUri = uri.toString(), tagId = finalTagId))
        }
    }

    fun removeTagFromImage(tagId: Long) {
        val uri = _selectedImageUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            tagDao.deleteTagFromMedia(uri.toString(), tagId)
            tagDao.deleteUnusedTags()
        }
    }

    fun exportTags(outputUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "exportTags started: $outputUri")
            _isExporting.value = true
            try {
                val associations = tagDao.getAllAssociations()
                com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "Found ${associations.size} associations")
                val grouped = associations.groupBy { it.mediaUri }
                    .mapValues { entry -> entry.value.map { it.tagName } }

                val json = JSONObject()
                json.put("version", 1)
                val dataArray = JSONArray()
                grouped.forEach { (uri, tagNames) ->
                    val item = JSONObject()
                    item.put("uri", uri)
                    val tagsArray = JSONArray()
                    tagNames.forEach { tagsArray.put(it) }
                    item.put("tags", tagsArray)
                    dataArray.put(item)
                }
                json.put("data", dataArray)

                val jsonString = json.toString(2)
                com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "JSON size: ${jsonString.length} chars")

                val stream = getApplication<Application>().contentResolver.openOutputStream(outputUri)
                if (stream == null) {
                    com.runerback.tagem.utils.AppLogger.e("GalleryViewModel", "openOutputStream returned null for $outputUri")
                    return@launch
                }
                stream.use {
                    it.write(jsonString.toByteArray())
                    com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "Export written successfully")
                }
            } catch (e: Exception) {
                com.runerback.tagem.utils.AppLogger.e("GalleryViewModel", "Export failed", e)
            } finally {
                _isExporting.value = false
                com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "exportTags finished")
            }
        }
    }

    fun importTags(inputUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "importTags started: $inputUri")
            _isImporting.value = true
            try {
                val stream = getApplication<Application>().contentResolver.openInputStream(inputUri)
                if (stream == null) {
                    com.runerback.tagem.utils.AppLogger.e("GalleryViewModel", "openInputStream returned null for $inputUri")
                    return@launch
                }
                val jsonString = stream.use { it.bufferedReader().readText() }
                com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "Import JSON size: ${jsonString.length} chars")

                val json = JSONObject(jsonString)
                val dataArray = json.getJSONArray("data")
                com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "Import entries: ${dataArray.length()}")

                tagDao.clearAll()

                val tagNameToId = mutableMapOf<String, Long>()

                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val uri = item.getString("uri")
                    val tagsArray = item.getJSONArray("tags")

                    tagDao.insertTaggedMedia(TaggedMediaEntity(mediaUri = uri))

                    for (j in 0 until tagsArray.length()) {
                        val tagName = tagsArray.getString(j)
                        val tagId = tagNameToId.getOrPut(tagName) {
                            val newId = tagDao.insertTag(TagEntity(name = tagName))
                            if (newId == -1L) {
                                tagDao.searchTags(tagName).first()
                                    .firstOrNull { it.name == tagName }?.id ?: -1L
                            } else {
                                newId
                            }
                        }
                        if (tagId == -1L) continue
                        tagDao.insertCrossRef(MediaTagCrossRef(mediaUri = uri, tagId = tagId))
                    }
                }
                com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "Import completed successfully")
            } catch (e: Exception) {
                com.runerback.tagem.utils.AppLogger.e("GalleryViewModel", "Import failed", e)
            } finally {
                _isImporting.value = false
                com.runerback.tagem.utils.AppLogger.d("GalleryViewModel", "importTags finished")
            }
        }
    }

    class Factory(
        private val application: Application,
        private val database: TagDatabase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GalleryViewModel(application, database.tagDao()) as T
        }
    }
}
