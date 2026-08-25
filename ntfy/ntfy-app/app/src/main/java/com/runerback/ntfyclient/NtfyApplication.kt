package com.runerback.ntfyclient

import android.app.Application
import com.runerback.ntfyclient.data.SubscriptionManager
import com.runerback.ntfyclient.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class NtfyApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob())
    lateinit var subscriptionManager: SubscriptionManager
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        NotificationHelper.createServiceChannel(this)
        subscriptionManager = SubscriptionManager(this, applicationScope)
        subscriptionManager.start()
    }
}
