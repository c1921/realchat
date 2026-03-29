package io.github.c1921.realchat.model

import kotlinx.serialization.Serializable

enum class ProviderType {
    DEEPSEEK,
    OPENAI,
    OPENAI_COMPATIBLE;

    fun defaultModel(): String {
        return when (this) {
            DEEPSEEK -> "deepseek-chat"
            OPENAI -> ""
            OPENAI_COMPATIBLE -> ""
        }
    }

    fun defaultBaseUrl(): String {
        return when (this) {
            DEEPSEEK -> "https://api.deepseek.com"
            OPENAI -> "https://api.openai.com/v1"
            OPENAI_COMPATIBLE -> ""
        }
    }
}

data class ProviderConfig(
    val providerType: ProviderType = DEFAULT_PROVIDER_TYPE,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val baseUrl: String = DEFAULT_BASE_URL
) {
    fun normalized(): ProviderConfig {
        return copy(
            apiKey = apiKey.trim(),
            model = model.trim(),
            baseUrl = baseUrl.trim()
        )
    }

    fun hasRequiredFields(): Boolean {
        val config = normalized()
        return config.apiKey.isNotEmpty() &&
            config.model.isNotEmpty() &&
            config.baseUrl.isNotEmpty()
    }

    companion object {
        val DEFAULT_PROVIDER_TYPE = ProviderType.DEEPSEEK
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"

        fun defaultsByProvider(): Map<ProviderType, ProviderConfig> {
            return ProviderType.entries.associateWith { providerType ->
                defaultsFor(providerType)
            }
        }

        fun defaultsFor(providerType: ProviderType): ProviderConfig {
            return ProviderConfig(
                providerType = providerType,
                model = providerType.defaultModel(),
                baseUrl = providerType.defaultBaseUrl()
            )
        }
    }
}

enum class ChatRole(val wireName: String) {
    System("system"),
    User("user"),
    Assistant("assistant")
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val createdAt: Long = 0L
)

enum class ConversationDebugSource(
    val wireName: String,
    val label: String
) {
    System("system", "系统"),
    Agent("agent", "Agent"),
    Director("director", "导演");

    companion object {
        fun fromWireName(value: String?): ConversationDebugSource? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}

enum class ConversationDebugType(val wireName: String) {
    SendStarted("send_started"),
    ProactiveTriggered("proactive_triggered"),
    ProactivePaused("proactive_paused"),
    DirectorAnalysisStarted("director_analysis_started"),
    DirectorAnalysisSucceeded("director_analysis_succeeded"),
    DirectorAnalysisFailed("director_analysis_failed"),
    ProactiveDecisionStarted("proactive_decision_started"),
    ProactiveDecisionSucceeded("proactive_decision_succeeded"),
    ProactiveDecisionFailed("proactive_decision_failed"),
    MemorySummaryStarted("memory_summary_started"),
    MemorySummarySucceeded("memory_summary_succeeded"),
    MemorySummaryFailed("memory_summary_failed"),
    PromptComposed("prompt_composed"),
    ChatRequestStarted("chat_request_started"),
    ChatRequestSucceeded("chat_request_succeeded"),
    ChatRequestFailed("chat_request_failed"),
    EmotionUpdateStarted("emotion_update_started"),
    EmotionUpdateSucceeded("emotion_update_succeeded"),
    EmotionUpdateFailed("emotion_update_failed");

    companion object {
        fun fromWireName(value: String?): ConversationDebugType? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}

data class ConversationDebugEvent(
    val id: Long = 0L,
    val conversationId: Long = 0L,
    val source: ConversationDebugSource = ConversationDebugSource.System,
    val type: ConversationDebugType = ConversationDebugType.SendStarted,
    val title: String = "",
    val summary: String = "",
    val details: String = "",
    val createdAt: Long = 0L
)

sealed interface ConversationTimelineItem {
    val createdAt: Long
    val stableKey: String
}

data class MessageTimelineItem(
    val message: ChatMessage,
    val optimistic: Boolean = false
) : ConversationTimelineItem {
    override val createdAt: Long = message.createdAt
    override val stableKey: String =
        buildString {
            append("message:")
            append(if (optimistic) "optimistic" else "persisted")
            append(':')
            append(createdAt)
            append(':')
            append(message.role.wireName)
            append(':')
            append(message.content.hashCode())
        }
}

data class DebugEventTimelineItem(
    val event: ConversationDebugEvent
) : ConversationTimelineItem {
    override val createdAt: Long = event.createdAt
    override val stableKey: String = "debug:${event.id}:${event.createdAt}"
}

@Serializable
data class UserPersona(
    val displayName: String = "",
    val description: String = ""
) {
    fun normalized(): UserPersona {
        return copy(
            displayName = displayName.trim(),
            description = description.trim()
        )
    }

    fun displayNameOrFallback(): String {
        return normalized().displayName.ifBlank { DEFAULT_NAME }
    }

    companion object {
        const val DEFAULT_NAME = "用户"
    }
}

@Serializable
data class CharacterCardSnapshot(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val creatorNotes: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "",
    val rawExtensionsJson: String = "{}",
    val rawUnknownJson: String = "{}"
) {
    fun normalized(): CharacterCardSnapshot {
        return copy(
            name = name.trim(),
            description = description.trim(),
            personality = personality.trim(),
            scenario = scenario.trim(),
            firstMes = firstMes.trim(),
            mesExample = mesExample.trim(),
            systemPrompt = systemPrompt.trim(),
            postHistoryInstructions = postHistoryInstructions.trim(),
            alternateGreetings = alternateGreetings.map(String::trim).filter(String::isNotEmpty),
            creatorNotes = creatorNotes.trim(),
            tags = tags.map(String::trim).filter(String::isNotEmpty),
            creator = creator.trim(),
            characterVersion = characterVersion.trim(),
            rawExtensionsJson = rawExtensionsJson.trim().ifEmpty { "{}" },
            rawUnknownJson = rawUnknownJson.trim().ifEmpty { "{}" }
        )
    }

    fun effectiveName(): String {
        return normalized().name.ifBlank { DEFAULT_CHARACTER_NAME }
    }

    fun preferredGreeting(): String {
        val normalized = normalized()
        return sequenceOf(normalized.firstMes)
            .plus(normalized.alternateGreetings.asSequence())
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            .orEmpty()
    }

    companion object {
        const val DEFAULT_CHARACTER_NAME = "通用助手"
    }
}

data class CharacterCard(
    val id: Long = 0L,
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val creatorNotes: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "",
    val rawExtensionsJson: String = "{}",
    val rawUnknownJson: String = "{}",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun normalized(): CharacterCard {
        val snapshot = toSnapshot().normalized()
        return copy(
            name = snapshot.name,
            description = snapshot.description,
            personality = snapshot.personality,
            scenario = snapshot.scenario,
            firstMes = snapshot.firstMes,
            mesExample = snapshot.mesExample,
            systemPrompt = snapshot.systemPrompt,
            postHistoryInstructions = snapshot.postHistoryInstructions,
            alternateGreetings = snapshot.alternateGreetings,
            creatorNotes = snapshot.creatorNotes,
            tags = snapshot.tags,
            creator = snapshot.creator,
            characterVersion = snapshot.characterVersion,
            rawExtensionsJson = snapshot.rawExtensionsJson,
            rawUnknownJson = snapshot.rawUnknownJson
        )
    }

    fun toSnapshot(): CharacterCardSnapshot {
        return CharacterCardSnapshot(
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            firstMes = firstMes,
            mesExample = mesExample,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            alternateGreetings = alternateGreetings,
            creatorNotes = creatorNotes,
            tags = tags,
            creator = creator,
            characterVersion = characterVersion,
            rawExtensionsJson = rawExtensionsJson,
            rawUnknownJson = rawUnknownJson
        )
    }
}

data class Conversation(
    val id: Long = 0L,
    val characterCardId: Long? = null,
    val characterSnapshot: CharacterCardSnapshot? = null,
    val draft: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val memorySummary: String = "",
    val emotionState: EmotionState = EmotionState()
)

data class ConversationListItem(
    val conversation: Conversation,
    val latestMessage: ChatMessage? = null
)

data class ConversationWithMessages(
    val conversation: Conversation,
    val messages: List<ChatMessage>,
    val debugEvents: List<ConversationDebugEvent> = emptyList()
)
