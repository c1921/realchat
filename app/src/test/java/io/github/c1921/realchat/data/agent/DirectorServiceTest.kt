package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.EmotionState
import io.github.c1921.realchat.model.ProactiveAction
import io.github.c1921.realchat.model.ProviderConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Test
    fun analyzeProactive_usesDedicatedPromptAndParsesDecision() = runTest {
        val chatProvider = CapturingChatProvider(
            response = ChatMessage(
                ChatRole.Assistant,
                "{\"action\":\"continue_current_thread\",\"mood\":\"克制\",\"topic_direction\":\"接住上一轮问题\",\"avoid\":\"像用户刚发来消息\",\"pursue\":\"自然跟进\",\"time_cue\":\"先不主动提时间\"}"
            )
        )
        val service = OpenAiCompatibleDirectorService(chatProvider = chatProvider)

        val result = service.analyzeProactive(
            snapshot = CharacterCardSnapshot(name = "Alice"),
            emotionState = EmotionState(affection = 75, mood = 2),
            conversationMessages = listOf(ChatMessage(ChatRole.User, "你好")),
            elapsedMs = 42 * 60_000L,
            config = ProviderConfig()
        ).getOrThrow()

        val systemPrompt = chatProvider.lastMessages.first().content
        assertTrue(systemPrompt.contains("你是主动消息导演"))
        assertTrue(systemPrompt.contains("wait_for_user"))
        assertTrue(systemPrompt.contains("{{elapsed_minutes}}").not())
        assertTrue(systemPrompt.contains("42"))
        assertEquals(ProactiveAction.CONTINUE_CURRENT_THREAD, result.action)
        assertEquals("先不主动提时间", result.timeCue)
    }
}

private class CapturingChatProvider(
    private val response: ChatMessage = ChatMessage(ChatRole.Assistant, "{\"mood\":\"温暖\"}")
) : ChatProvider {
    var lastMessages: List<ChatMessage> = emptyList()

    override suspend fun send(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<ChatMessage> {
        lastMessages = messages
        return Result.success(response)
    }
}
