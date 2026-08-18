package com.runerback.queuehelper

import android.app.Application
import com.runerback.queuehelper.data.local.TaskRepository
import com.runerback.queuehelper.data.template.TemplateLoader

class QueueHelperApplication : Application() {

    lateinit var taskRepository: TaskRepository
        private set

    lateinit var templateLoader: TemplateLoader
        private set

    override fun onCreate() {
        super.onCreate()
        taskRepository = TaskRepository(this)
        templateLoader = TemplateLoader(this)
    }
}
