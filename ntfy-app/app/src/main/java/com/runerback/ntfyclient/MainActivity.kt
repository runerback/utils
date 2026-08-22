package com.runerback.ntfyclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.ntfyclient.ui.NtfyClientScreen
import com.runerback.ntfyclient.ui.theme.NtfyClientTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NtfyClientTheme {
                NtfyClientScreen()
            }
        }
    }
}
