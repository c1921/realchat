package io.github.c1921.realchat.data.chat

import androidx.room.withTransaction
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.Conversation
import io.github.c1921.realchat.model.ConversationListItem
import io.github.c1921.realchat.model.ConversationWithMessages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeConversationListItems(): Flow<List<ConversationListItem>>

    fun observeConversationWithMessages(conversationId: Long): Flow<ConversationWithMessages?>

    suspend fun getConversationById(conversationId: Long): Conversation?

    suspend fun createConversation(
        characterCard: CharacterCard,
        title: String = ""
    ): Long

    suspend fun updateDraft(conversationId: Long, draft: String)

    suspend fun appendSuccessfulExchange(
        conversationId: Long,
        userContent: String,
        assistantMessage: ChatMessage
    )

    suspend fun renameConversation(conversationId: Long, title: String)

    suspend fun deleteConversation(conversationId: Long)

    suspend fun ensureConversationExists(characterCard: CharacterCard): Long
}

class RoomConversationRepository(
    private val database: AppDatabase,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) : ConversationRepository {
    private val conversationDao = database.conversationDao()
    private val messageDao = database.conversationMessageDao()

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.observeAll().map { conversations ->
            conversations.map { entity -> entity.toDomain() }
        }
    }

    override fun observeConversationListItems(): Flow<List<ConversationListItem>> {
        return conversationDao.observeListItems().map { rows ->
            rows.map { row ->
                ConversationListItem(
                    conversation = row.conversation.toDomain(),
                    latestMessage = row.latestMessageContent?.let { content ->
                        ChatMessage(
                            role = ChatRole.entries.firstOrNull { it.wireName == row.latestMessageRole }
                                ?: ChatRole.Assistant,
                            content = content
                        )
                    }
                )
            }
        }
    }

    override fun observeConversationWithMessages(conversationId: Long): Flow<ConversationWithMessages?> {
        return combine(
            conversationDao.observeById(conversationId),
            messageDao.observeByConversationId(conversationId)
        ) { conversation, messages ->
            conversation?.toDomain()?.let { domainConversation ->
                ConversationWithMessages(
                    conversation = domainConversation,
                    messages = messages.map { entity -> entity.toDomain() }
                )
            }
        }
    }

    override suspend fun getConversationById(conversationId: Long): Conversation? {
        return conversationDao.getById(conversationId)?.toDomain()
    }

    override suspend fun createConversation(
        characterCard: CharacterCard,
        title: String
    ): Long {
        val snapshot = characterCard.toSnapshot().normalized()
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val conversationId = conversationDao.insert(
                ConversationEntity(
                    title = title.trim().ifBlank { snapshot.effectiveName() },
                    characterCardId = characterCard.id.takeIf { it != 0L },
                    characterSnapshotJson = json.encodeToString(snapshot),
                    draft = "",
                    createdAt = now,
                    updatedAt = now
                )
            )

            val greeting = snapshot.preferredGreeting()
            if (greeting.isNotBlank()) {
                messageDao.insert(
                    ConversationMessageEntity(
                        conversationId = conversationId,
                        role = ChatRole.Assistant.wireName,
                        content = greeting,
                        createdAt = now
                    )
                )
            }
            conversationId
        }
    }

    override suspend fun updateDraft(conversationId: Long, draft: String) {
        val existing = conversationDao.getById(conversationId) ?: return
        conversationDao.update(
            existing.copy(
                draft = draft,
                updatedAt = existing.updatedAt
            )
        )
    }

    override suspend fun appendSuccessfulExchange(
        conversationId: Long,
        userContent: String,
        assistantMessage: ChatMessage
    ) {
        val existing = conversationDao.getById(conversationId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            messageDao.insert(
                ConversationMessageEntity(
                    conversationId = conversationId,
                    role = ChatRole.User.wireName,
                    content = userContent,
                    createdAt = now
                )
            )
            messageDao.insert(
                ConversationMessageEntity(
                    conversationId = conversationId,
                    role = assistantMessage.role.wireName,
                    content = assistantMessage.content,
                    createdAt = now
                )
            )
            conversationDao.update(
                existing.copy(
                    draft = "",
                    updatedAt = now
                )
            )
        }
    }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        val existing = conversationDao.getById(conversationId) ?: return
        conversationDao.update(
            existing.copy(
                title = title.trim().ifBlank { existing.title },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteConversation(conversationId: Long) {
        val existing = conversationDao.getById(conversationId) ?: return
        conversationDao.delete(existing)
    }

    override suspend fun ensureConversationExists(characterCard: CharacterCard): Long {
        if (conversationDao.count() > 0) {
            return conversationDao.getMostRecent()?.id ?: 0L
        }
        return createConversation(characterCard = characterCard)
    }

    private fun ConversationEntity.toDomain(): Conversation {
        return Conversation(
            id = id,
            title = title,
            characterCardId = characterCardId,
            characterSnapshot = characterSnapshotJson
                ?.takeIf(String::isNotBlank)
                ?.let { snapshotJson ->
                    runCatching {
                        json.decodeFromString<CharacterCardSnapshot>(snapshotJson)
                    }.getOrNull()
                },
            draft = draft,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun ConversationMessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            role = ChatRole.entries.firstOrNull { it.wireName == role } ?: ChatRole.Assistant,
            content = content
        )
    }
}
