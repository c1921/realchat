package io.github.c1921.realchat.data.chat

import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.ProviderType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiCompatibleChatProviderTest {
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
            "https://api.openai.com/v1/chat/completions",
            buildChatCompletionsUrl("https://api.openai.com/v1")
        )
    }

    @Test
    fun extractProviderErrorMessage_prefersNestedErrorPayload() {
        val message = extractProviderErrorMessage(
            statusCode = 401,
            responseBody = """{"error":{"message":"Invalid API key"}}"""
        )

        assertEquals("Invalid API key", message)
    }

    @Test
    fun send_postsChatCompletionsRequestAndParsesResponse() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "你好"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val provider = OpenAiCompatibleChatProvider()
            val result = provider.send(
                messages = listOf(
                    ChatMessage(ChatRole.System, "系统提示"),
                    ChatMessage(ChatRole.User, "你好")
                ),
                config = ProviderConfig(
                    providerType = ProviderType.OPENAI_COMPATIBLE,
                    apiKey = "test-key",
                    model = "gpt-4o-mini",
                    baseUrl = server.url("/v1").toString()
                )
            )

            assertEquals("你好", result.getOrThrow().message.content)

            val recordedRequest = server.takeRequest()
            assertEquals("/v1/chat/completions", recordedRequest.path)
            assertEquals("Bearer test-key", recordedRequest.getHeader("Authorization"))

            val payload = Json.parseToJsonElement(recordedRequest.body.readUtf8()).jsonObject
            assertEquals("gpt-4o-mini", payload.getValue("model").jsonPrimitive.content)
            val messages = payload.getValue("messages").jsonArray
            assertEquals("system", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
            assertEquals("系统提示", messages[0].jsonObject.getValue("content").jsonPrimitive.content)
            assertEquals("user", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
            assertEquals("你好", messages[1].jsonObject.getValue("content").jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }
}
