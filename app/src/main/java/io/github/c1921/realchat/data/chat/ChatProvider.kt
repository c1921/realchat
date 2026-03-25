package io.github.c1921.realchat.data.chat

import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ProviderConfig

interface ChatProvider {
    suspend fun send(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<ChatMessage>
}
