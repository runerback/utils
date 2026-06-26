package com.runerback.drawer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.drawer.ui.screens.DrawerScreen
import com.runerback.drawer.ui.theme.DrawerTheme
import com.runerback.drawer.util.LogManager

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.d(TAG, "onCreate")
        enableEdgeToEdge()
        try {
            setContent {
                DrawerTheme {
                    DrawerScreen()
                }
            }
            LogManager.d(TAG, "setContent succeeded")
        } catch (e: Exception) {
            LogManager.e(TAG, "setContent failed", e)
            throw e
        }
    }
}
