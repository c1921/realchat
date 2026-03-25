package io.github.c1921.realchat.model

data class ProviderConfig(
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
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    }
}

enum class ChatRole(val wireName: String) {
    User("user"),
    Assistant("assistant")
}

data class ChatMessage(
    val role: ChatRole,
    val content: String
)
