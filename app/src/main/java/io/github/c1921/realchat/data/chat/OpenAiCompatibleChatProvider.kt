package io.github.c1921.realchat.data.chat

import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.ProviderConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal fun buildChatCompletionsUrl(baseUrl: String): String {
    return baseUrl.trim().trimEnd('/') + "/chat/completions"
}

internal fun extractProviderErrorMessage(statusCode: Int, responseBody: String): String {
    if (responseBody.isBlank()) {
        return "请求失败（HTTP $statusCode）。"
    }

    val jsonElement = runCatching {
        Json.parseToJsonElement(responseBody)
    }.getOrNull()

    val root = jsonElement as? JsonObject
    val nestedError = root
        ?.get("error")
        ?.jsonObject
        ?.get("message")
        ?.jsonPrimitive
        ?.contentOrNull
    val topLevelMessage = root
        ?.get("message")
        ?.jsonPrimitive
        ?.contentOrNull
    val fallbackMessage = root
        ?.get("error_msg")
        ?.jsonPrimitive
        ?.contentOrNull

    return listOf(nestedError, topLevelMessage, fallbackMessage)
        .firstOrNull { !it.isNullOrBlank() }
        ?: "请求失败（HTTP $statusCode）。"
}

class OpenAiCompatibleChatProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) : ChatProvider {
    override suspend fun send(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<ChatMessage> {
        val normalizedConfig = config.normalized()
        val requestUrl = buildChatCompletionsUrl(normalizedConfig.baseUrl)
        val httpUrl = requestUrl.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Base URL 无效。"))

        val requestBody = OpenAiCompatibleChatRequest(
            model = normalizedConfig.model,
            messages = messages.map { message ->
                OpenAiCompatibleWireMessage(
                    role = message.role.wireName,
                    content = message.content
                )
            }
        )

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(httpUrl)
                    .header("Authorization", "Bearer ${normalizedConfig.apiKey}")
                    .header("Content-Type", "application/json")
                    .post(
                        json.encodeToString(requestBody)
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException(
                            extractProviderErrorMessage(
                                statusCode = response.code,
                                responseBody = responseText
                            )
                        )
                    }

                    val responseBodyModel =
                        json.decodeFromString<OpenAiCompatibleChatResponse>(responseText)
                    val content = responseBodyModel.choices
                        .firstOrNull()
                        ?.message
                        ?.content
                        ?.trim()
                        .orEmpty()

                    if (content.isEmpty()) {
                        throw IOException("服务端未返回有效回复。")
                    }

                    ChatMessage(
                        role = ChatRole.Assistant,
                        content = content
                    )
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = {
                    Result.failure(IOException(it.message ?: "请求失败。", it))
                }
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class OpenAiCompatibleChatRequest(
    val model: String,
    val messages: List<OpenAiCompatibleWireMessage>
)

@Serializable
private data class OpenAiCompatibleWireMessage(
    val role: String,
    val content: String
)

@Serializable
private data class OpenAiCompatibleChatResponse(
    val choices: List<OpenAiCompatibleChoice> = emptyList()
)

@Serializable
private data class OpenAiCompatibleChoice(
    val message: OpenAiCompatibleWireMessage? = null
)
