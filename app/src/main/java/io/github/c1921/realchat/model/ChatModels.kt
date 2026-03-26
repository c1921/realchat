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
    val content: String
)

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
    val updatedAt: Long = 0L
)

data class ConversationListItem(
    val conversation: Conversation,
    val latestMessage: ChatMessage? = null
)

data class ConversationWithMessages(
    val conversation: Conversation,
    val messages: List<ChatMessage>
)
