package com.runerback.queuehelper

import android.app.Application
import com.runerback.queuehelper.data.local.QueueJobRepository
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.template.TemplateLoader
import com.runerback.queuehelper.ui.components.LogBuffer

class QueueHelperApplication : Application() {

    lateinit var taskRepository: TaskRepository
        private set

    lateinit var queueJobRepository: QueueJobRepository
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
        taskRepository = TaskRepository(this)
        queueJobRepository = QueueJobRepository(this)
        templateLoader = TemplateLoader(this)
    }
}
