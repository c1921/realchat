package io.github.c1921.realchat.data.chat

import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.UserPersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptComposerTest {
    private val composer = PromptComposer()

    @Test
    fun compose_wrapsHistoryWithSystemPromptPersonaAndTailInstruction() {
        val result = composer.compose(
            characterSnapshot = CharacterCardSnapshot(
                name = "Alice",
                description = "侦探",
                personality = "冷静",
                scenario = "在案发现场",
                mesExample = "{{char}}：继续调查。",
                systemPrompt = "你是 {{char}}。{{original}}",
                postHistoryInstructions = "只输出 {{char}} 的回复。"
            ),
            userPersona = UserPersona(
                displayName = "Bob",
                description = "委托人"
            ),
            conversationMessages = listOf(
                ChatMessage(ChatRole.Assistant, "我到了。"),
                ChatMessage(ChatRole.User, "开始吧。")
            )
        )

        assertEquals(ChatRole.System, result.first().role)
        assertEquals(ChatRole.System, result.last().role)
        assertTrue(result[0].content.contains("Alice"))
        assertTrue(result[0].content.contains("长期连续对话"))
        assertTrue(result.any { it.role == ChatRole.System && it.content.contains("用户 Persona") })
        assertTrue(result.any { it.role == ChatRole.System && it.content.contains("继续调查") })
        assertEquals(ChatRole.Assistant, result[4].role)
        assertEquals(ChatRole.User, result[5].role)
    }
}
