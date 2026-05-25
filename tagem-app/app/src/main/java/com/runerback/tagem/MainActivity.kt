package com.runerback.tagem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.runerback.tagem.ui.GalleryViewModel
import com.runerback.tagem.ui.MainScreen
import com.runerback.tagem.ui.theme.TagEmTheme

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<GalleryViewModel> {
        GalleryViewModel.Factory(application, (application as TagEmApplication).database)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadImages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermission()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            TagEmTheme {
                MainScreen(
                    uiState = uiState,
                    onToggleTagPanel = viewModel::toggleTagPanel,
                    onSelectTag = viewModel::selectTag,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onSelectImage = viewModel::selectImage,
                    onDismissEditor = viewModel::dismissEditor,
                    onAddTag = viewModel::addTagToImage,
                    onRemoveTag = viewModel::removeTagFromImage,
                    onShareImage = { uri -> shareImage(uri) },
                    onRefresh = viewModel::loadImages,
                    onToggleGifsOnly = viewModel::toggleShowGifsOnly,
                )
            }
        }
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.loadImages()
            }

            else -> {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
    }

    private fun shareImage(uri: android.net.Uri) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri) ?: "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, "Share image"))
    }
}
