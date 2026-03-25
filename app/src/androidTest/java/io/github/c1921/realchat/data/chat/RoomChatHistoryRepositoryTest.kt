package io.github.c1921.realchat.data.chat

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomChatHistoryRepositoryTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var databases = mutableListOf<AppDatabase>()

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        databases.forEach(AppDatabase::close)
        databases.clear()
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun observeState_returnsEmptyStateForFreshDatabase() = runBlocking {
        val repository = inMemoryRepository()

        val state = repository.observeState().first()

        assertTrue(state.messages.isEmpty())
        assertEquals("", state.draft)
    }

    @Test
    fun updateDraft_persistsDraft() = runBlocking {
        val repository = inMemoryRepository()

        repository.updateDraft("待发送内容")
        val state = repository.observeState().first { it.draft == "待发送内容" }

        assertEquals("待发送内容", state.draft)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun appendSuccessfulExchange_keepsUserAssistantOrder() = runBlocking {
        val repository = inMemoryRepository()

        repository.appendSuccessfulExchange(
            userContent = "你好",
            assistantMessage = ChatMessage(ChatRole.Assistant, "世界")
        )
        val state = repository.observeState().first { it.messages.size == 2 }

        assertEquals(
            listOf(
                ChatMessage(ChatRole.User, "你好"),
                ChatMessage(ChatRole.Assistant, "世界")
            ),
            state.messages
        )
        assertEquals("", state.draft)
    }

    @Test
    fun fileDatabase_restoresMessagesAfterReopen() = runBlocking {
        val firstRepository = fileRepository()

        firstRepository.appendSuccessfulExchange(
            userContent = "问题",
            assistantMessage = ChatMessage(ChatRole.Assistant, "回答")
        )
        databases.removeLast().close()

        val reopenedRepository = fileRepository()
        val state = reopenedRepository.observeState().first { it.messages.size == 2 }

        assertEquals(
            listOf(
                ChatMessage(ChatRole.User, "问题"),
                ChatMessage(ChatRole.Assistant, "回答")
            ),
            state.messages
        )
        assertEquals("", state.draft)
    }

    private fun inMemoryRepository(): RoomChatHistoryRepository {
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        databases += database
        return RoomChatHistoryRepository(database.chatHistoryDao())
    }

    private fun fileRepository(): RoomChatHistoryRepository {
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME
        ).allowMainThreadQueries().build()
        databases += database
        return RoomChatHistoryRepository(database.chatHistoryDao())
    }

    private companion object {
        const val TEST_DATABASE_NAME = "room-chat-history-test.db"
    }
}
