package com.runerback.translator

import android.app.Application
import com.runerback.translator.data.SettingsManager
import com.runerback.translator.util.LogManager

class TranslatorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LogManager.init(this)
        SettingsManager.init(this)
        LogManager.d("TranslatorApplication", "onCreate")

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            LogManager.eSync("TranslatorApplication", "Uncaught exception on ${thread.name}", throwable)
            val message = throwable.message ?: throwable.javaClass.simpleName
            startActivity(ErrorActivity.createIntent(this, message))
        }
    }
}
