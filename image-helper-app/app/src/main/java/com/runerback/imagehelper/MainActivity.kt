package com.runerback.imagehelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.runerback.imagehelper.ui.screens.DrawerScreen
import com.runerback.imagehelper.ui.screens.GifMakerScreen
import com.runerback.imagehelper.ui.screens.MenuScreen
import com.runerback.imagehelper.ui.screens.SubMenu
import com.runerback.imagehelper.ui.theme.ImageHelperTheme
import com.runerback.imagehelper.util.LogManager

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
                ImageHelperTheme {
                    var currentSubMenu by rememberSaveable { mutableStateOf<String?>(null) }

                    val subMenu = currentSubMenu?.let { SubMenu.valueOf(it) }
                    if (subMenu != null) {
                        BackHandler { currentSubMenu = null }
                    }

                    when (subMenu) {
                        null -> MenuScreen(onNavigate = { currentSubMenu = it.name })
                        SubMenu.DRAWER -> DrawerScreen()
                        SubMenu.GIF_MAKER -> GifMakerScreen()
                    }
                }
            }
            LogManager.d(TAG, "setContent succeeded")
        } catch (e: Exception) {
            LogManager.e(TAG, "setContent failed", e)
            throw e
        }
    }
}
