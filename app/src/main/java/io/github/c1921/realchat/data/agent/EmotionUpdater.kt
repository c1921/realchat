package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.EmotionState
import io.github.c1921.realchat.model.ProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface EmotionUpdater {
    suspend fun update(
        currentState: EmotionState,
        snapshot: CharacterCardSnapshot?,
        recentMessages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<EmotionState>
}

class OpenAiCompatibleEmotionUpdater(
    private val chatProvider: ChatProvider
) : EmotionUpdater {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun update(
        currentState: EmotionState,
        snapshot: CharacterCardSnapshot?,
        recentMessages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<EmotionState> {
        val characterName = snapshot?.effectiveName().orEmpty()
        val prompt = SYSTEM_PROMPT
            .replace("{{char}}", characterName)
            .replace("{{affection}}", currentState.affection.toString())
            .replace("{{mood}}", currentState.mood.toString())

        val historyText = recentMessages.takeLast(MAX_RECENT_MESSAGES).joinToString("\n") { msg ->
            val roleLabel = when (msg.role) {
                ChatRole.User -> "用户"
                ChatRole.Assistant -> characterName.ifBlank { "角色" }
                ChatRole.System -> "[系统]"
            }
            "$roleLabel: ${msg.content}"
        }

        val messages = listOf(
            ChatMessage(role = ChatRole.System, content = prompt),
            ChatMessage(role = ChatRole.User, content = historyText)
        )

        return chatProvider.send(messages, config).mapCatching { response ->
            parseEmotionState(response.content, currentState)
        }
    }

    private fun parseEmotionState(rawText: String, fallback: EmotionState): EmotionState {
        val trimmed = rawText.trim()
        val jsonStr = extractJson(trimmed) ?: return fallback
        return runCatching {
            val element = json.parseToJsonElement(jsonStr).jsonObject
            EmotionState(
                affection = element["affection"]?.jsonPrimitive?.int ?: fallback.affection,
                mood = element["mood"]?.jsonPrimitive?.int ?: fallback.mood
            ).normalized()
        }.getOrDefault(fallback)
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    companion object {
        private const val MAX_RECENT_MESSAGES = 10

        const val SYSTEM_PROMPT =
            "根据以下对话，评估角色 {{char}} 对用户的情绪变化。" +
                "当前情绪：好感度 {{affection}}/100，心情 {{mood}}（-5 到 5）。" +
                "只输出 JSON，格式：{\"affection\":<0-100>,\"mood\":<-5到5>}，不要解释。"
    }
}
