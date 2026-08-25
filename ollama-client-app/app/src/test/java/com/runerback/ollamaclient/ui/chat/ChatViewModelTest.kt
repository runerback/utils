package com.runerback.ollamaclient.ui.chat

import com.runerback.ollamaclient.data.local.FakeSettingsRepository
import com.runerback.ollamaclient.data.model.Message
import com.runerback.ollamaclient.data.remote.OllamaApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var settingsRepository: FakeSettingsRepository

    @Before
    fun setup() {
        settingsRepository = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `assistant message id is preserved across streaming chunks`() = runTest {
        val chunks = listOf(
            Message(id = "chunk-1", role = "assistant", content = "chunk 1"),
            Message(id = "chunk-2", role = "assistant", content = "chunk 2"),
            Message(id = "chunk-3", role = "assistant", content = "chunk 3"),
        )
        val fakeApiService = object : OllamaApiService() {
            override fun chat(
                baseUrl: String,
                model: String,
                messages: List<Message>,
                think: Boolean,
            ): Flow<Message> = flow {
                chunks.forEach { emit(it) }
            }
        }

        settingsRepository.setModel("test-model")

        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val viewModel = ChatViewModel(settingsRepository, fakeApiService)

        viewModel.send("hi")
        advanceUntilIdle()

        val finalMessages = viewModel.messages.value
        assertEquals(2, finalMessages.size)
        assertEquals("user", finalMessages[0].role)
        assertEquals("hi", finalMessages[0].content)

        val assistantMessage = finalMessages[1]
        assertEquals("assistant", assistantMessage.role)
        assertEquals("chunk 3", assistantMessage.content)

        val chunkIds = chunks.map { it.id }.toSet()
        assertNotEquals(
            "assistant message id was replaced by a chunk id; it should be preserved",
            chunkIds,
            setOf(assistantMessage.id),
        )
        chunkIds.forEach { chunkId ->
            assertNotEquals(chunkId, assistantMessage.id)
        }
    }
}
