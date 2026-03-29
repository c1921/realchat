package io.github.c1921.realchat.data.chat

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import io.github.c1921.realchat.model.ConversationDebugSource
import io.github.c1921.realchat.model.ConversationDebugType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                ChatRole.Assistant to "我到了。",
                ChatRole.User to "你好",
                ChatRole.Assistant to "继续。"
            ),
            bundle?.messages?.map { it.role to it.content }
        )
    }

    @Test
    fun appendDebugEvent_persistsAndRestoresConversationLogs() = runBlocking {
        val database = inMemoryDatabase()
        val repository = RoomConversationRepository(database)
        val conversationId = repository.createConversation(
            CharacterCard(id = 3L, name = "调试角色")
        )

        repository.appendDebugEvent(
            conversationId = conversationId,
            source = ConversationDebugSource.Director,
            type = ConversationDebugType.DirectorAnalysisSucceeded,
            title = "导演分析完成",
            summary = "氛围：温暖",
            details = "原始输出\n{\"mood\":\"温暖\"}",
            createdAt = 1234L
        )

        val bundle = repository.observeConversationWithMessages(conversationId)
            .first { it?.debugEvents?.isNotEmpty() == true }

        assertEquals(1, bundle?.debugEvents?.size)
        assertEquals("导演分析完成", bundle?.debugEvents?.single()?.title)
        assertEquals("氛围：温暖", bundle?.debugEvents?.single()?.summary)
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

    @Test
    fun observeConversationListItems_returnsLatestMessageAndUpdatedOrder() = runBlocking {
        val database = inMemoryDatabase()
        val conversationRepository = RoomConversationRepository(database)
        val firstConversationId = conversationRepository.createConversation(
            CharacterCard(
                id = 1L,
                name = "Alice",
                firstMes = "初始问候"
            )
        )
        val secondConversationId = conversationRepository.createConversation(
            CharacterCard(
                id = 2L,
                name = "Bob",
                firstMes = "第二个问候"
            )
        )

        conversationRepository.appendSuccessfulExchange(
            conversationId = firstConversationId,
            userContent = "更新一下",
            assistantMessage = ChatMessage(ChatRole.Assistant, "最新回复")
        )

        val items = conversationRepository.observeConversationListItems()
            .first { it.size == 2 }

        assertEquals(listOf(firstConversationId, secondConversationId), items.map { it.conversation.id })
        assertEquals("最新回复", items.first().latestMessage?.content)
        assertEquals(ChatRole.Assistant, items.first().latestMessage?.role)
        assertEquals("第二个问候", items.last().latestMessage?.content)
    }

    @Test
    fun migrateFrom2To3_removesTitleColumnAndKeepsConversationData() = runBlocking {
        seedVersion2Database()

        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME
        )
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        databases += database

        val columns = mutableListOf<String>()
        database.openHelper.writableDatabase
            .query("PRAGMA table_info(`conversations`)")
            .use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }

        assertFalse(columns.contains("title"))

        val repository = RoomConversationRepository(database)
        val conversations = repository.observeConversations().first()
        val bundle = repository.observeConversationWithMessages(1L).first()

        assertEquals(1, conversations.size)
        assertEquals("旧角色", conversations.single().characterSnapshot?.effectiveName())
        assertEquals("迁移前的草稿", conversations.single().draft)
        assertEquals("旧消息", bundle?.messages?.single()?.content)
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
        )
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        databases += database
        return database
    }

    private fun seedVersion2Database() {
        val databaseFile = context.getDatabasePath(TEST_DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `character_cards` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `personality` TEXT NOT NULL,
                    `scenario` TEXT NOT NULL,
                    `firstMes` TEXT NOT NULL,
                    `mesExample` TEXT NOT NULL,
                    `systemPrompt` TEXT NOT NULL,
                    `postHistoryInstructions` TEXT NOT NULL,
                    `alternateGreetingsSerialized` TEXT NOT NULL,
                    `creatorNotes` TEXT NOT NULL,
                    `tagsSerialized` TEXT NOT NULL,
                    `creator` TEXT NOT NULL,
                    `characterVersion` TEXT NOT NULL,
                    `rawExtensionsJson` TEXT NOT NULL,
                    `rawUnknownJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `characterCardId` INTEGER,
                    `characterSnapshotJson` TEXT,
                    `draft` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversation_messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `conversationId` INTEGER NOT NULL,
                    `role` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_character_cards_updatedAt` ON `character_cards` (`updatedAt`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversations_updatedAt` ON `conversations` (`updatedAt`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversations_characterCardId` ON `conversations` (`characterCardId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_conversation_messages_conversationId` ON `conversation_messages` (`conversationId`)"
            )
            database.execSQL(
                """
                INSERT INTO `conversations` (
                    `id`,
                    `title`,
                    `characterCardId`,
                    `characterSnapshotJson`,
                    `draft`,
                    `createdAt`,
                    `updatedAt`
                ) VALUES (
                    1,
                    '旧标题',
                    NULL,
                    '{"name":"旧角色"}',
                    '迁移前的草稿',
                    100,
                    200
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `conversation_messages` (
                    `id`,
                    `conversationId`,
                    `role`,
                    `content`,
                    `createdAt`
                ) VALUES (
                    1,
                    1,
                    'assistant',
                    '旧消息',
                    201
                )
                """.trimIndent()
            )
            database.version = 2
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "room-conversation-test.db"
    }
}
