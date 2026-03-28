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

enum class ProactiveAction(val wireName: String) {
    CONTINUE_CURRENT_THREAD("continue_current_thread"),
    START_NEW_TOPIC("start_new_topic"),
    WAIT_FOR_USER("wait_for_user");

    companion object {
        fun fromWireName(value: String?): ProactiveAction? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}

data class ProactiveInstruction(
    val action: ProactiveAction,
    val timeCue: String = ""
)

data class ProactiveDirectorDecision(
    val action: ProactiveAction,
    val mood: String = "",
    val topicDirection: String = "",
    val avoid: String = "",
    val pursue: String = "",
    val timeCue: String = "",
    val rawJson: String = ""
) {
    fun toGuidance(): DirectorGuidance = DirectorGuidance(
        mood = mood,
        topicDirection = topicDirection,
        avoid = avoid,
        pursue = pursue,
        rawJson = rawJson
    )

    fun toInstruction(): ProactiveInstruction = ProactiveInstruction(
        action = action,
        timeCue = timeCue
    )
}
