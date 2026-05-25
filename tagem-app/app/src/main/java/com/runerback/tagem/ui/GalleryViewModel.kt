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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _allImages = MutableStateFlow<List<ImageStore.ImageItem>>(emptyList())
    private val _selectedTagId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _tagPanelOpen = MutableStateFlow(false)
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    private val _showGifsOnly = MutableStateFlow(false)

    private data class ImageFilterResult(
        val allImages: List<ImageStore.ImageItem>,
        val selectedTagId: Long?,
        val showGifsOnly: Boolean,
        val filteredImages: List<ImageStore.ImageItem>,
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

    init {
        viewModelScope.launch {
            combine(
                imageFilterFlow,
                tagsFlow,
                selectedImageTagsFlow,
                _tagPanelOpen,
                tagCountsFlow,
            ) { imageFilter, tags, selectedImageTags, tagPanelOpen, tagCounts ->
                UiState(
                    images = imageFilter.allImages,
                    filteredImages = imageFilter.filteredImages,
                    tags = tags,
                    selectedTagId = imageFilter.selectedTagId,
                    searchQuery = _searchQuery.value,
                    tagPanelOpen = tagPanelOpen,
                    selectedImageUri = _selectedImageUri.value,
                    selectedImageTags = selectedImageTags,
                    showGifsOnly = imageFilter.showGifsOnly,
                    tagCounts = tagCounts,
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
