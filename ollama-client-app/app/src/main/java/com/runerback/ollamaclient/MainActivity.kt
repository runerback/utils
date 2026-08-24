package com.runerback.ollamaclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.ollamaclient.ui.OllamaClientScreen
import com.runerback.ollamaclient.ui.theme.OllamaClientTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OllamaClientTheme {
                OllamaClientScreen()
            }
        }
    }
}
