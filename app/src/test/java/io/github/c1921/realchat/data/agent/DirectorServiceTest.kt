package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.EmotionState
import io.github.c1921.realchat.model.ProviderConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorServiceTest {
    @Test
    fun analyze_usesDefaultPromptThatForbidsStateUpdateStyleOutput() = runTest {
        val chatProvider = CapturingChatProvider()
        val service = OpenAiCompatibleDirectorService(chatProvider = chatProvider)

        service.analyze(
            snapshot = CharacterCardSnapshot(name = "Alice"),
            emotionState = EmotionState(affection = 75, mood = 2),
            conversationMessages = listOf(ChatMessage(ChatRole.User, "你好")),
            config = ProviderConfig()
        )

        val systemPrompt = chatProvider.lastMessages.first().content
        assertTrue(systemPrompt.contains("不要复述底层分数"))
        assertTrue(systemPrompt.contains("不要复述底层分数、数值变化或“状态更新”说明"))
    }
}

private class CapturingChatProvider : ChatProvider {
    var lastMessages: List<ChatMessage> = emptyList()

    override suspend fun send(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<ChatMessage> {
        lastMessages = messages
        return Result.success(ChatMessage(ChatRole.Assistant, "{\"mood\":\"温暖\"}"))
    }
}
