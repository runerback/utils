package com.runerback.queuehelper.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.runerback.queuehelper.ui.navigation.QueueNavHost

@Composable
fun QueueHelperScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    QueueNavHost(
        navController = navController,
        modifier = modifier
    )
}
