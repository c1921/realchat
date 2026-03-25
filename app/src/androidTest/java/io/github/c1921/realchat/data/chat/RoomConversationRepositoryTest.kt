package io.github.c1921.realchat.data.chat

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomConversationRepositoryTest {
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
    fun createConversation_persistsGreetingAndMessages() = runBlocking {
        val database = inMemoryDatabase()
        val conversationRepository = RoomConversationRepository(database)
        val card = CharacterCard(
            id = 11L,
            name = "Alice",
            firstMes = "我到了。"
        )

        val conversationId = conversationRepository.createConversation(card)
        conversationRepository.appendSuccessfulExchange(
            conversationId = conversationId,
            userContent = "你好",
            assistantMessage = ChatMessage(ChatRole.Assistant, "继续。")
        )
        val bundle = conversationRepository.observeConversationWithMessages(conversationId)
            .first { it?.messages?.size == 3 }

        assertEquals("Alice", bundle?.conversation?.characterSnapshot?.effectiveName())
        assertEquals(
            listOf(
                ChatMessage(ChatRole.Assistant, "我到了。"),
                ChatMessage(ChatRole.User, "你好"),
                ChatMessage(ChatRole.Assistant, "继续。")
            ),
            bundle?.messages
        )
    }

    @Test
    fun fileDatabase_restoresConversationAfterReopen() = runBlocking {
        val firstDatabase = fileDatabase()
        val firstRepository = RoomConversationRepository(firstDatabase)
        val card = CharacterCard(
            id = 7L,
            name = "Eve",
            firstMes = "上线。"
        )

        val conversationId = firstRepository.createConversation(card)
        firstRepository.appendSuccessfulExchange(
            conversationId = conversationId,
            userContent = "状态如何",
            assistantMessage = ChatMessage(ChatRole.Assistant, "一切正常。")
        )
        databases.removeLast().close()

        val reopenedDatabase = fileDatabase()
        val reopenedRepository = RoomConversationRepository(reopenedDatabase)
        val conversations = reopenedRepository.observeConversations().first()
        val bundle = reopenedRepository.observeConversationWithMessages(conversations.first().id)
            .first { it?.messages?.size == 3 }

        assertTrue(conversations.isNotEmpty())
        assertEquals("Eve", conversations.first().characterSnapshot?.effectiveName())
        assertEquals("上线。", bundle?.messages?.first()?.content)
    }

    private fun inMemoryDatabase(): AppDatabase {
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        databases += database
        return database
    }

    private fun fileDatabase(): AppDatabase {
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME
        ).allowMainThreadQueries().build()
        databases += database
        return database
    }

    private companion object {
        const val TEST_DATABASE_NAME = "room-conversation-test.db"
    }
}
