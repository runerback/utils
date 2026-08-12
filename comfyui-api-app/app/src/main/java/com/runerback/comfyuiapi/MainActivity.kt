package com.runerback.comfyuiapi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.runerback.comfyuiapi.ui.MainScreen
import com.runerback.comfyuiapi.ui.MainViewModel
import com.runerback.comfyuiapi.ui.gallery.OutputGalleryScreen
import com.runerback.comfyuiapi.ui.schemagenerator.SchemaGeneratorScreen
import com.runerback.comfyuiapi.ui.schemagenerator.SchemaGeneratorViewModel
import com.runerback.comfyuiapi.ui.theme.ComfyUIApiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val schemaGeneratorViewModel: SchemaGeneratorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComfyUIApiTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Main) }

                BackHandler(enabled = currentScreen != AppScreen.Main) {
                    currentScreen = AppScreen.Main
                }

                when (currentScreen) {
                    AppScreen.Main -> MainScreen(
                        viewModel = mainViewModel,
                        onOpenSchemaGenerator = { currentScreen = AppScreen.SchemaGenerator },
                        onOpenGallery = { currentScreen = AppScreen.OutputGallery }
                    )
                    AppScreen.SchemaGenerator -> SchemaGeneratorScreen(
                        viewModel = schemaGeneratorViewModel,
                        onBack = { currentScreen = AppScreen.Main }
                    )
                    AppScreen.OutputGallery -> OutputGalleryScreen(
                        viewModel = mainViewModel,
                        onBack = { currentScreen = AppScreen.Main }
                    )
                }
            }
        }
    }
}

private enum class AppScreen {
    Main,
    SchemaGenerator,
    OutputGallery
}
