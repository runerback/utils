package com.runerback.comfyuiapi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.comfyuiapi.ui.MainScreen
import com.runerback.comfyuiapi.ui.MainViewModel
import com.runerback.comfyuiapi.ui.theme.ComfyUIApiTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComfyUIApiTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
