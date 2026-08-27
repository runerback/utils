package com.runerback.ntfymgr

import android.app.Application
import com.runerback.ntfymgr.data.local.SettingsRepository
import com.runerback.ntfymgr.data.local.TokenRepository
import com.runerback.ntfymgr.data.remote.NtfyMgrApi

class NtfyApplication : Application() {

    val tokenRepository by lazy { TokenRepository(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val api by lazy { NtfyMgrApi() }

    override fun onCreate() {
        super.onCreate()
        api.token = tokenRepository.getToken()
    }
}
