package io.github.c1921.realchat.data.chat

import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ProviderConfig
import java.io.IOException

data class ChatRequestTrace(
    val requestMessages: List<ChatMessage> = emptyList(),
    val requestUrl: String = "",
    val model: String = "",
    val rawResponseBody: String = "",
    val responseContent: String = ""
)

data class ChatProviderResult(
    val message: ChatMessage,
    val trace: ChatRequestTrace = ChatRequestTrace()
)

class ChatRequestException(
    message: String,
    cause: Throwable? = null,
    val trace: ChatRequestTrace = ChatRequestTrace()
) : IOException(message, cause)

interface ChatProvider {
    suspend fun send(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<ChatProviderResult>
}
