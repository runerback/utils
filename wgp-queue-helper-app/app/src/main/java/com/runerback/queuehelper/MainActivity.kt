package com.runerback.queuehelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runerback.queuehelper.ui.QueueHelperScreen
import com.runerback.queuehelper.ui.theme.QueueHelperTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QueueHelperTheme {
                QueueHelperScreen()
            }
        }
    }
}
