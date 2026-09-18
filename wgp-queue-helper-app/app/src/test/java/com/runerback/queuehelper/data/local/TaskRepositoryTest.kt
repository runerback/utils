package com.runerback.queuehelper.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.runerback.queuehelper.data.model.Task
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            repository = TaskRepository(context)
            repository.deleteAllTasks()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            repository.deleteAllTasks()
        }
    }

    @Test
    fun `deleteAllGlobalTasks clears global count snapshot`() = runBlocking {
        val globalTask = Task(
            id = 1,
            presetId = 0,
            createdAt = System.currentTimeMillis(),
            payload = taskPayload(1),
            createdInGlobal = true
        )

        repository.saveTask(globalTask)
        repository.deleteAllGlobalTasks()

        assertTrue(repository.loadGlobalTasks().isEmpty())
        assertEquals(0, repository.countTasksByPreset().global)
    }

    private fun taskPayload(id: Int) = buildJsonObject {
        put("id", id)
        put("params", buildJsonObject { put("id", id) })
    }
}
