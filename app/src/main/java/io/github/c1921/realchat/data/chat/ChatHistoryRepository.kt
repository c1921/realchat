package io.github.c1921.realchat.data.chat

import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class PersistedChatState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = ""
)

interface ChatHistoryRepository {
    fun observeState(): Flow<PersistedChatState>

    suspend fun updateDraft(draft: String)

    suspend fun appendSuccessfulExchange(
        userContent: String,
        assistantMessage: ChatMessage
    )
}

class RoomChatHistoryRepository(
    private val chatHistoryDao: ChatHistoryDao
) : ChatHistoryRepository {
    override fun observeState(): Flow<PersistedChatState> {
        return combine(
            chatHistoryDao.observeMessages(),
            chatHistoryDao.observeDraft()
        ) { messages, draft ->
            PersistedChatState(
                messages = messages.map { entity ->
                    ChatMessage(
                        role = entity.toDomainRole(),
                        content = entity.content
                    )
                },
                draft = draft.orEmpty()
            )
        }
    }

    override suspend fun updateDraft(draft: String) {
        chatHistoryDao.upsertSession(
            ChatSessionEntity(
                sessionId = ChatSessionEntity.DEFAULT_SESSION_ID,
                draft = draft
            )
        )
    }

    override suspend fun appendSuccessfulExchange(
        userContent: String,
        assistantMessage: ChatMessage
    ) {
        chatHistoryDao.appendSuccessfulExchange(
            userContent = userContent,
            assistantRole = assistantMessage.role.wireName,
            assistantContent = assistantMessage.content
        )
    }
}

private fun ChatMessageEntity.toDomainRole(): ChatRole {
    return ChatRole.entries.firstOrNull { it.wireName == role } ?: ChatRole.Assistant
}
