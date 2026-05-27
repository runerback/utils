package com.runerback.remotecp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.remotecp.ui.screens.RoomScreen
import com.runerback.remotecp.ui.theme.RemoteCPTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteCPTheme {
                RoomScreen()
            }
        }
    }
}
