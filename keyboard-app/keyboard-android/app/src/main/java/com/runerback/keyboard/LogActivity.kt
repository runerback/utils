package com.runerback.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.keyboard.ui.screens.LogScreen
import com.runerback.keyboard.ui.theme.KeyboardTheme
import com.runerback.keyboard.util.LogManager

class LogActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LogActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.d(TAG, "onCreate")
        enableEdgeToEdge()

        setContent {
            KeyboardTheme {
                LogScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}
