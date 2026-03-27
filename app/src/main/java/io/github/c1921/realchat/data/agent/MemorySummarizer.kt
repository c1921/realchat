package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.ProviderConfig

interface MemorySummarizer {
    suspend fun summarize(
        messagesToSummarize: List<ChatMessage>,
        snapshot: CharacterCardSnapshot?,
        config: ProviderConfig
    ): Result<String>
}

class OpenAiCompatibleMemorySummarizer(
    private val chatProvider: ChatProvider
) : MemorySummarizer {

    override suspend fun summarize(
        messagesToSummarize: List<ChatMessage>,
        snapshot: CharacterCardSnapshot?,
        config: ProviderConfig
    ): Result<String> {
        val characterName = snapshot?.effectiveName().orEmpty()
        val historyText = messagesToSummarize.joinToString("\n") { msg ->
            val roleLabel = when (msg.role) {
                ChatRole.User -> "用户"
                ChatRole.Assistant -> characterName.ifBlank { "角色" }
                ChatRole.System -> "[系统]"
            }
            "$roleLabel: ${msg.content}"
        }

        val systemPrompt = SYSTEM_PROMPT.replace("{{char}}", characterName)
        val messages = listOf(
            ChatMessage(role = ChatRole.System, content = systemPrompt),
            ChatMessage(role = ChatRole.User, content = historyText)
        )

        return chatProvider.send(messages, config).map { response -> response.content.trim() }
    }

    companion object {
        const val SYSTEM_PROMPT =
            "将以下对话内容压缩为简洁摘要。使用第三人称，保留关键情节、情感变化和重要信息。" +
                "角色名为 {{char}}。直接输出摘要文本，不要加标题或解释。"
    }
}
