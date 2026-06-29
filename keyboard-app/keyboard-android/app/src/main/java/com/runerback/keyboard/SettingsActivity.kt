package com.runerback.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.keyboard.ui.screens.SettingsScreen
import com.runerback.keyboard.ui.theme.KeyboardTheme
import com.runerback.keyboard.util.LogManager

class SettingsActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.d(TAG, "onCreate")
        enableEdgeToEdge()

        setContent {
            KeyboardTheme {
                SettingsScreen()
            }
        }
    }
}
