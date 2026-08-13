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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
                val canEditCurrentSchema = uiState.hasSchema && uiState.parameters.isNotEmpty()

                BackHandler(enabled = currentScreen != AppScreen.Main) {
                    if (currentScreen == AppScreen.SchemaGenerator) {
                        schemaGeneratorViewModel.exitEditMode()
                    }
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
                        canEditCurrentSchema = canEditCurrentSchema,
                        currentSchema = mainViewModel.loadedSchema,
                        currentWorkflow = mainViewModel.loadedWorkflow,
                        currentWorkflowName = uiState.workflowName,
                        onSchemaEdited = { newSchema ->
                            mainViewModel.reloadSchema(newSchema)
                            currentScreen = AppScreen.Main
                        },
                        onBack = {
                            schemaGeneratorViewModel.exitEditMode()
                            currentScreen = AppScreen.Main
                        }
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
