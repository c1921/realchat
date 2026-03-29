package io.github.c1921.realchat.data.chat

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "character_cards",
    indices = [Index(value = ["updatedAt"])]
)
data class CharacterCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMes: String,
    val mesExample: String,
    val systemPrompt: String,
    val postHistoryInstructions: String,
    val alternateGreetingsSerialized: String,
    val creatorNotes: String,
    val tagsSerialized: String,
    val creator: String,
    val characterVersion: String,
    val rawExtensionsJson: String,
    val rawUnknownJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"]), Index(value = ["characterCardId"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val characterCardId: Long?,
    val characterSnapshotJson: String?,
    val draft: String,
    val createdAt: Long,
    val updatedAt: Long,
    val memorySummary: String = "",
    val emotionStateJson: String = DEFAULT_EMOTION_STATE_JSON
) {
    companion object {
        const val DEFAULT_EMOTION_STATE_JSON = "{\"affection\":50,\"mood\":0}"
    }
}

@Entity(
    tableName = "conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: Long
)

@Entity(
    tableName = "conversation_debug_events",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"]), Index(value = ["createdAt"])]
)
data class ConversationDebugEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val conversationId: Long,
    val source: String,
    val type: String,
    val title: String,
    val summary: String,
    val details: String,
    val createdAt: Long
)

data class ConversationListItemRow(
    @Embedded
    val conversation: ConversationEntity,
    @ColumnInfo(name = "latestMessageRole")
    val latestMessageRole: String?,
    @ColumnInfo(name = "latestMessageContent")
    val latestMessageContent: String?
)

@Dao
interface CharacterCardDao {
    @Query("SELECT * FROM character_cards ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<CharacterCardEntity>>

    @Query("SELECT * FROM character_cards WHERE id = :id")
    suspend fun getById(id: Long): CharacterCardEntity?

    @Query("SELECT * FROM character_cards ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getMostRecent(): CharacterCardEntity?

    @Query("SELECT COUNT(*) FROM character_cards")
    suspend fun count(): Int

    @Insert
    suspend fun insert(card: CharacterCardEntity): Long

    @Update
    suspend fun update(card: CharacterCardEntity)

    @Delete
    suspend fun delete(card: CharacterCardEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT
            conversations.*,
            (
                SELECT role
                FROM conversation_messages
                WHERE conversationId = conversations.id
                ORDER BY id DESC
                LIMIT 1
            ) AS latestMessageRole,
            (
                SELECT content
                FROM conversation_messages
                WHERE conversationId = conversations.id
                ORDER BY id DESC
                LIMIT 1
            ) AS latestMessageContent
        FROM conversations
        ORDER BY updatedAt DESC, id DESC
        """
    )
    fun observeListItems(): Flow<List<ConversationListItemRow>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getMostRecent(): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)
}

@Dao
interface ConversationMessageDao {
    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeByConversationId(conversationId: Long): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getByConversationId(conversationId: Long): List<ConversationMessageEntity>

    @Insert
    suspend fun insert(message: ConversationMessageEntity): Long

    @Query("DELETE FROM conversation_messages WHERE conversationId = :conversationId AND id NOT IN (:keepIds)")
    suspend fun deleteExcept(conversationId: Long, keepIds: List<Long>)

    @Query("DELETE FROM conversation_messages WHERE conversationId = :conversationId")
    suspend fun deleteAll(conversationId: Long)
}

@Dao
interface ConversationDebugEventDao {
    @Query(
        "SELECT * FROM conversation_debug_events WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC"
    )
    fun observeByConversationId(conversationId: Long): Flow<List<ConversationDebugEventEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: ConversationDebugEventEntity): Long
}

@Database(
    entities = [
        CharacterCardEntity::class,
        ConversationEntity::class,
        ConversationMessageEntity::class,
        ConversationDebugEventEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterCardDao(): CharacterCardDao

    abstract fun conversationDao(): ConversationDao

    abstract fun conversationMessageDao(): ConversationMessageDao

    abstract fun conversationDebugEventDao(): ConversationDebugEventDao

    companion object {
        private const val DATABASE_NAME = "realchat.db"

        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
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
                    )
                    VALUES (
                        1,
                        '默认对话',
                        NULL,
                        NULL,
                        COALESCE(
                            (
                                SELECT `draft`
                                FROM `chat_session`
                                WHERE `sessionId` = 1
                            ),
                            ''
                        ),
                        $now,
                        $now
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `conversation_messages` (
                        `conversationId`,
                        `role`,
                        `content`,
                        `createdAt`
                    )
                    SELECT
                        1,
                        `role`,
                        `content`,
                        $now
                    FROM `chat_messages`
                    ORDER BY `id` ASC
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE IF EXISTS `chat_messages`")
                database.execSQL("DROP TABLE IF EXISTS `chat_session`")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("PRAGMA foreign_keys=OFF")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversations_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
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
                    INSERT INTO `conversations_new` (
                        `id`,
                        `characterCardId`,
                        `characterSnapshotJson`,
                        `draft`,
                        `createdAt`,
                        `updatedAt`
                    )
                    SELECT
                        `id`,
                        `characterCardId`,
                        `characterSnapshotJson`,
                        `draft`,
                        `createdAt`,
                        `updatedAt`
                    FROM `conversations`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `conversations`")
                database.execSQL("ALTER TABLE `conversations_new` RENAME TO `conversations`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversations_updatedAt` ON `conversations` (`updatedAt`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversations_characterCardId` ON `conversations` (`characterCardId`)"
                )
                database.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `conversations` ADD COLUMN `memorySummary` TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE `conversations` ADD COLUMN `emotionStateJson` TEXT NOT NULL DEFAULT '${ConversationEntity.DEFAULT_EMOTION_STATE_JSON}'"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversation_debug_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conversationId` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversation_debug_events_conversationId` ON `conversation_debug_events` (`conversationId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversation_debug_events_createdAt` ON `conversation_debug_events` (`createdAt`)"
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5
        )

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { database ->
                        instance = database
                    }
            }
        }
    }
}
