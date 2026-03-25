package io.github.c1921.realchat.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekChatProviderTest {
    @Test
    fun buildChatCompletionsUrl_acceptsOfficialBaseUrlVariants() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            buildChatCompletionsUrl("https://api.deepseek.com")
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            buildChatCompletionsUrl("https://api.deepseek.com/")
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            buildChatCompletionsUrl("https://api.deepseek.com/v1")
        )
    }

    @Test
    fun extractDeepSeekErrorMessage_prefersNestedErrorPayload() {
        val message = extractDeepSeekErrorMessage(
            statusCode = 401,
            responseBody = """{"error":{"message":"Invalid API key"}}"""
        )

        assertEquals("Invalid API key", message)
    }
}
