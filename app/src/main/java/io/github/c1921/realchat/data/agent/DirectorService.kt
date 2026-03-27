package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.DirectorGuidance
import io.github.c1921.realchat.model.EmotionState
import io.github.c1921.realchat.model.ProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface DirectorService {
    suspend fun analyze(
        snapshot: CharacterCardSnapshot?,
        emotionState: EmotionState,
        conversationMessages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<DirectorGuidance>
}

class OpenAiCompatibleDirectorService(
    private val chatProvider: ChatProvider,
    private var systemPrompt: String = ""
) : DirectorService {

    private val json = Json { ignoreUnknownKeys = true }

    fun updateSystemPrompt(prompt: String) {
        systemPrompt = prompt
    }

    override suspend fun analyze(
        snapshot: CharacterCardSnapshot?,
        emotionState: EmotionState,
        conversationMessages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<DirectorGuidance> {
        val characterName = snapshot?.effectiveName().orEmpty()
        val effectivePrompt = systemPrompt.takeUnless { it.isBlank() }
            ?: DEFAULT_DIRECTOR_SYSTEM_PROMPT
        val resolvedPrompt = effectivePrompt
            .replace("{{affection}}", emotionState.affection.toString())
            .replace("{{mood}}", emotionState.mood.toString())
            .replace("{{char}}", characterName)

        val recentMessages = conversationMessages.takeLast(MAX_HISTORY_MESSAGES)
        val messages = listOf(ChatMessage(role = ChatRole.System, content = resolvedPrompt)) +
            recentMessages

        return chatProvider.send(messages, config).mapCatching { response ->
            parseGuidance(response.content)
        }
    }

    private fun parseGuidance(rawText: String): DirectorGuidance {
        val trimmed = rawText.trim()
        val jsonStr = extractJson(trimmed) ?: trimmed
        return runCatching {
            val element = json.parseToJsonElement(jsonStr).jsonObject
            DirectorGuidance(
                mood = element["mood"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                topicDirection = element["topic_direction"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                avoid = element["avoid"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                pursue = element["pursue"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                rawJson = jsonStr
            )
        }.getOrElse {
            DirectorGuidance(rawJson = trimmed)
        }
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 20

        const val DEFAULT_DIRECTOR_SYSTEM_PROMPT =
            "你是叙事导演。根据对话历史和角色 {{char}} 当前情绪（好感度:{{affection}}/100，心情:{{mood}}/5），" +
                "输出 JSON 指导下一条回复：" +
                "{\"mood\":\"<氛围>\",\"topic_direction\":\"<话题走向>\",\"avoid\":\"<避免的内容>\",\"pursue\":\"<推进的内容>\"}" +
                "只输出 JSON，不要解释。"
    }
}
