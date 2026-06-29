package com.runerback.keyboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.runerback.keyboard.SettingsActivity
import com.runerback.keyboard.data.SettingsRepository
import com.runerback.keyboard.network.KeyboardClient
import com.runerback.keyboard.ui.screens.KeyboardScreen
import com.runerback.keyboard.ui.theme.KeyboardTheme
import com.runerback.keyboard.util.LogManager

class KeyboardInputMethodService : InputMethodService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        LogManager.d("KeyboardIME", "onCreate")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val host = SettingsRepository.readHost()
        val port = SettingsRepository.readPort()
        if (host.isNotBlank()) {
            KeyboardClient.sendConfig(SettingsRepository.readInterceptRealKeyboard())
            KeyboardClient.connect(host, port, SettingsRepository.readDeviceToken())
        }
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val host = SettingsRepository.readHost()
        val port = SettingsRepository.readPort()
        if (host.isNotBlank() && KeyboardClient.state.value !is KeyboardClient.State.Connected) {
            KeyboardClient.sendConfig(SettingsRepository.readInterceptRealKeyboard())
            KeyboardClient.connect(host, port, SettingsRepository.readDeviceToken())
        }

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@KeyboardInputMethodService)
            setContent {
                KeyboardTheme {
                    val connectionState by KeyboardClient.state.collectAsState()
                    KeyboardScreen(
                        onKeyEvent = { vk, action ->
                            KeyboardClient.sendKey(vk, action)
                        },
                        connectionState = connectionState,
                        onOpenSettings = {
                            val intent = Intent(
                                this@KeyboardInputMethodService,
                                SettingsActivity::class.java
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
