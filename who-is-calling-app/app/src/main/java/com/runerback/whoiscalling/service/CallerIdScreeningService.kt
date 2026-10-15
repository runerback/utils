package com.runerback.whoiscalling.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.runerback.whoiscalling.data.api.LookupApi
import com.runerback.whoiscalling.data.settings.SettingsRepository
import com.runerback.whoiscalling.notify.CallInfoNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class CallerIdScreeningService : CallScreeningService() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var lookupApi: LookupApi
    @Inject lateinit var notifier: CallInfoNotifier
    @Inject lateinit var recentLookups: RecentLookupsStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            allowCall(callDetails)
            return
        }
        val number = callDetails.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        if (number == null) {
            allowCall(callDetails)
            return
        }

        serviceScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.enabled || settings.apiUrlTemplate.isBlank()) {
                allowCall(callDetails)
                return@launch
            }

            var response = CallResponse.Builder().build()
            try {
                val result = withTimeoutOrNull(settings.timeoutMillis) {
                    lookupApi.lookup(settings.apiUrlTemplate, number, settings.timeoutMillis).getOrThrow()
                }
                if (result != null) {
                    val blocked = result.isSpam && settings.blockSpam
                    if (blocked) {
                        response = CallResponse.Builder()
                            .setDisallowCall(true)
                            .setRejectCall(true)
                            .setSkipCallLog(false)
                            .setSkipNotification(true)
                            .build()
                    }
                    notifier.showCallInfo(number, result, blocked)
                    recentLookups.add(number, result, blocked, error = null)
                } else {
                    if (settings.notifyOnLookupFailure) {
                        notifier.showLookupFailure(number, "timeout after ${settings.timeoutMillis} ms")
                    }
                    recentLookups.add(number, result = null, blocked = false, error = "timeout")
                }
            } catch (e: Exception) {
                if (settings.notifyOnLookupFailure) {
                    notifier.showLookupFailure(number, e.message ?: e.javaClass.simpleName)
                }
                recentLookups.add(number, result = null, blocked = false, error = e.message)
            } finally {
                respondToCall(callDetails, response)
            }
        }
    }

    private fun allowCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
