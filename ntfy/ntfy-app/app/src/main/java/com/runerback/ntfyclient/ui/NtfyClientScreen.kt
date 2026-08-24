package com.runerback.ntfyclient.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.runerback.ntfyclient.ui.home.HomeScreen

@Composable
fun NtfyClientScreen() {
    Scaffold { innerPadding ->
        HomeScreen(modifier = Modifier.padding(innerPadding))
    }
}
