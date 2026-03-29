package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.model.AgentExecutionException
import io.github.c1921.realchat.model.AgentExecutionTrace
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.DirectorGuidance
import io.github.c1921.realchat.model.EmotionState
import io.github.c1921.realchat.model.ProactiveAction
import io.github.c1921.realchat.model.ProactiveDirectorDecision
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.TracedValue
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
    ): Result<TracedValue<DirectorGuidance>>

    suspend fun analyzeProactive(
        snapshot: CharacterCardSnapshot?,
        emotionState: EmotionState,
        conversationMessages: List<ChatMessage>,
        elapsedMs: Long,
        config: ProviderConfig
    ): Result<TracedValue<ProactiveDirectorDecision>>
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
    ): Result<TracedValue<DirectorGuidance>> {
        val characterName = snapshot?.effectiveName().orEmpty()
        val effectivePrompt = systemPrompt.takeUnless { it.isBlank() }
            ?: DEFAULT_DIRECTOR_SYSTEM_PROMPT
        val resolvedPrompt = resolvePrompt(
            template = effectivePrompt,
            characterName = characterName,
            emotionState = emotionState
        )

        val recentMessages = conversationMessages.takeLast(MAX_HISTORY_MESSAGES)
        val messages = listOf(ChatMessage(role = ChatRole.System, content = resolvedPrompt)) +
            recentMessages

        return chatProvider.send(messages, config).fold(
            onSuccess = { response ->
                val rawOutput = response.message.content
                runCatching {
                    val guidance = parseGuidance(rawOutput)
                    TracedValue(
                        value = guidance,
                        trace = AgentExecutionTrace(
                            systemPrompt = resolvedPrompt,
                            requestMessages = messages,
                            rawOutput = rawOutput,
                            parsedSummary = summarizeGuidance(guidance)
                        )
                    )
                }.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = {
                        Result.failure(
                            AgentExecutionException(
                                message = it.message ?: "导演输出解析失败。",
                                cause = it,
                                trace = AgentExecutionTrace(
                                    systemPrompt = resolvedPrompt,
                                    requestMessages = messages,
                                    rawOutput = rawOutput
                                )
                            )
                        )
                    }
                )
            },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun analyzeProactive(
        snapshot: CharacterCardSnapshot?,
        emotionState: EmotionState,
        conversationMessages: List<ChatMessage>,
        elapsedMs: Long,
        config: ProviderConfig
    ): Result<TracedValue<ProactiveDirectorDecision>> {
        val characterName = snapshot?.effectiveName().orEmpty()
        val elapsedMinutes = (elapsedMs / 60_000L).coerceAtLeast(0L)
        val resolvedPrompt = resolvePrompt(
            template = DEFAULT_PROACTIVE_DIRECTOR_SYSTEM_PROMPT,
            characterName = characterName,
            emotionState = emotionState
        ).replace("{{elapsed_minutes}}", elapsedMinutes.toString())

        val recentMessages = conversationMessages.takeLast(MAX_HISTORY_MESSAGES)
        val messages = listOf(ChatMessage(role = ChatRole.System, content = resolvedPrompt)) +
            recentMessages

        return chatProvider.send(messages, config).fold(
            onSuccess = { response ->
                val rawOutput = response.message.content
                runCatching {
                    val decision = parseProactiveDecision(rawOutput)
                    TracedValue(
                        value = decision,
                        trace = AgentExecutionTrace(
                            systemPrompt = resolvedPrompt,
                            requestMessages = messages,
                            rawOutput = rawOutput,
                            parsedSummary = summarizeDecision(decision)
                        )
                    )
                }.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = {
                        Result.failure(
                            AgentExecutionException(
                                message = it.message ?: "主动导演输出解析失败。",
                                cause = it,
                                trace = AgentExecutionTrace(
                                    systemPrompt = resolvedPrompt,
                                    requestMessages = messages,
                                    rawOutput = rawOutput
                                )
                            )
                        )
                    }
                )
            },
            onFailure = { Result.failure(it) }
        )
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

    private fun parseProactiveDecision(rawText: String): ProactiveDirectorDecision {
        val trimmed = rawText.trim()
        val jsonStr = extractJson(trimmed) ?: trimmed
        val element = json.parseToJsonElement(jsonStr).jsonObject
        val action = ProactiveAction.fromWireName(
            element["action"]?.jsonPrimitive?.contentOrNull
        ) ?: throw IllegalArgumentException("主动导演输出缺少有效 action。")

        return ProactiveDirectorDecision(
            action = action,
            mood = element["mood"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            topicDirection = element["topic_direction"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            avoid = element["avoid"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pursue = element["pursue"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            timeCue = element["time_cue"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            rawJson = jsonStr
        )
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun resolvePrompt(
        template: String,
        characterName: String,
        emotionState: EmotionState
    ): String {
        return template
            .replace("{{affection}}", emotionState.affection.toString())
            .replace("{{mood}}", emotionState.mood.toString())
            .replace("{{char}}", characterName)
    }

    private fun summarizeGuidance(guidance: DirectorGuidance): String {
        return buildList {
            if (guidance.mood.isNotBlank()) add("氛围：${guidance.mood}")
            if (guidance.topicDirection.isNotBlank()) add("话题方向：${guidance.topicDirection}")
            if (guidance.pursue.isNotBlank()) add("推进：${guidance.pursue}")
            if (guidance.avoid.isNotBlank()) add("避免：${guidance.avoid}")
        }.joinToString("，")
    }

    private fun summarizeDecision(decision: ProactiveDirectorDecision): String {
        return buildList {
            add("动作：${decision.action.wireName}")
            if (decision.mood.isNotBlank()) add("氛围：${decision.mood}")
            if (decision.topicDirection.isNotBlank()) add("话题方向：${decision.topicDirection}")
            if (decision.pursue.isNotBlank()) add("推进：${decision.pursue}")
            if (decision.avoid.isNotBlank()) add("避免：${decision.avoid}")
            if (decision.timeCue.isNotBlank()) add("时间表达：${decision.timeCue}")
        }.joinToString("，")
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 20

        const val DEFAULT_DIRECTOR_SYSTEM_PROMPT =
            "你是叙事导演。根据对话历史和角色 {{char}} 当前情绪（好感度:{{affection}}/100，心情:{{mood}}/5），" +
                "输出 JSON 指导下一条回复：" +
                "{\"mood\":\"<氛围>\",\"topic_direction\":\"<话题走向>\",\"avoid\":\"<避免的内容>\",\"pursue\":\"<推进的内容>\"}" +
                "只输出用于指导回复方向的 JSON，不要复述底层分数、数值变化或“状态更新”说明，不要解释。"

        const val DEFAULT_PROACTIVE_DIRECTOR_SYSTEM_PROMPT =
            "你是主动消息导演。根据对话历史、角色 {{char}} 当前情绪（好感度:{{affection}}/100，心情:{{mood}}/5）以及用户最近约 {{elapsed_minutes}} 分钟未发言，" +
                "判断这一轮是否应由 {{char}} 主动联系用户。输出 JSON：" +
                "{\"action\":\"continue_current_thread|start_new_topic|wait_for_user\",\"mood\":\"<氛围>\",\"topic_direction\":\"<话题走向>\",\"avoid\":\"<避免的内容>\",\"pursue\":\"<推进的内容>\",\"time_cue\":\"<如需提及时间时使用的自然表达，可留空>\"}" +
                "如果上一话题未结束或仍有自然跟进空间，使用 continue_current_thread；如果上一轮已自然结束且适合重新开启聊天，使用 start_new_topic；如果此时不应主动打扰用户，使用 wait_for_user。" +
                "时间表达由你决定是否提及以及如何自然表达，不要复述底层分数，不要解释，只输出 JSON。"
    }
}
