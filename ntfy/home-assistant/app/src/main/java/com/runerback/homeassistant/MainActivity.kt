package com.runerback.homeassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.homeassistant.ui.HomeAssistantApp
import com.runerback.homeassistant.ui.theme.HomeAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeAssistantTheme {
                HomeAssistantApp()
            }
        }
    }
}
