package io.github.c1921.realchat.model

data class ProactiveSettings(
    val enabled: Boolean = false,
    val minIntervalMinutes: Int = 30,
    val maxIntervalMinutes: Int = 1440,
    val maxCount: Int = 5
) {
    val minIntervalMs: Long get() = minIntervalMinutes * 60_000L
    val maxIntervalMs: Long get() = maxIntervalMinutes * 60_000L
}

data class DirectorSettings(
    val enabled: Boolean = false,
    val systemPrompt: String = ""
)

data class EmotionState(
    val affection: Int = 50,
    val mood: Int = 0
) {
    fun normalized(): EmotionState = copy(
        affection = affection.coerceIn(0, 100),
        mood = mood.coerceIn(-5, 5)
    )
}

data class MemorySettings(
    val enabled: Boolean = false,
    val triggerCount: Int = 40,
    val keepRecentCount: Int = 10
)

data class AgentSettings(
    val proactive: ProactiveSettings = ProactiveSettings(),
    val director: DirectorSettings = DirectorSettings(),
    val memory: MemorySettings = MemorySettings()
)

data class DirectorGuidance(
    val mood: String = "",
    val topicDirection: String = "",
    val avoid: String = "",
    val pursue: String = "",
    val rawJson: String = ""
)
