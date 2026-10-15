package com.runerback.whoiscalling

import android.app.Application
import com.runerback.whoiscalling.notify.CallInfoNotifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WhoIsCallingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CallInfoNotifier.createChannel(this)
    }
}
