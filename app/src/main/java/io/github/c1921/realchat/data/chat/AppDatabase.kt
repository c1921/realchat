package io.github.c1921.realchat.data.chat

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val role: String,
    val content: String
)

@Entity(tableName = "chat_session")
data class ChatSessionEntity(
    @PrimaryKey
    val sessionId: Int = DEFAULT_SESSION_ID,
    val draft: String = ""
) {
    companion object {
        const val DEFAULT_SESSION_ID = 1
    }
}

@Dao
interface ChatHistoryDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT draft
        FROM chat_session
        WHERE sessionId = ${ChatSessionEntity.DEFAULT_SESSION_ID}
        """
    )
    fun observeDraft(): Flow<String?>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Transaction
    suspend fun appendSuccessfulExchange(
        userContent: String,
        assistantRole: String,
        assistantContent: String
    ) {
        insertMessage(
            ChatMessageEntity(
                role = "user",
                content = userContent
            )
        )
        insertMessage(
            ChatMessageEntity(
                role = assistantRole,
                content = assistantContent
            )
        )
        upsertSession(
            ChatSessionEntity(
                sessionId = ChatSessionEntity.DEFAULT_SESSION_ID,
                draft = ""
            )
        )
    }
}

@Database(
    entities = [ChatMessageEntity::class, ChatSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        private const val DATABASE_NAME = "realchat.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { database ->
                    instance = database
                }
            }
        }
    }
}
