package com.runerback.ntfymgr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runerback.ntfymgr.ui.home.HomeScreen
import com.runerback.ntfymgr.ui.home.HomeViewModel
import com.runerback.ntfymgr.ui.theme.NtfyMgrTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NtfyApplication
        app.api.token = app.tokenRepository.getToken()

        setContent {
            NtfyMgrTheme {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        api = app.api,
                        tokenRepository = app.tokenRepository,
                        settingsRepository = app.settingsRepository,
                    )
                )
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}
