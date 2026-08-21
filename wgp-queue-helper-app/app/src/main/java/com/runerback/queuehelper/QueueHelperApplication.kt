package com.runerback.queuehelper

import android.app.Application
import com.runerback.queuehelper.data.local.MediaRepository
import com.runerback.queuehelper.data.local.PresetRepository
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.template.TemplateLoader
import com.runerback.queuehelper.ui.components.LogBuffer

class QueueHelperApplication : Application() {

    lateinit var presetRepository: PresetRepository
        private set

    lateinit var taskRepository: TaskRepository
        private set

    lateinit var mediaRepository: MediaRepository
        private set

    lateinit var templateLoader: TemplateLoader
        private set

    override fun onCreate() {
        super.onCreate()
        LogBuffer.init(this)
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            LogBuffer.add("Uncaught exception: ${throwable.stackTraceToString()}")
            LogBuffer.copyToDownloads(this)
        }
        presetRepository = PresetRepository(this)
        taskRepository = TaskRepository(this)
        mediaRepository = MediaRepository(this)
        templateLoader = TemplateLoader(this)
    }
}
