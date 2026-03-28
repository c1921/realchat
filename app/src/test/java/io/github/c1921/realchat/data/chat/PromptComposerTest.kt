package io.github.c1921.realchat.data.chat

import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.DirectorGuidance
import io.github.c1921.realchat.model.ProactiveAction
import io.github.c1921.realchat.model.ProactiveInstruction
import io.github.c1921.realchat.model.UserPersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptComposerTest {
    private val composer = PromptComposer()

    private val snapshot = CharacterCardSnapshot(
        name = "Alice",
        description = "侦探",
        personality = "冷静",
        scenario = "在案发现场",
        mesExample = "{{char}}：继续调查。",
        systemPrompt = "你是 {{char}}。{{original}}",
        postHistoryInstructions = "只输出 {{char}} 的回复。"
    )

    private val persona = UserPersona(displayName = "Bob", description = "委托人")

    private val history = listOf(
        ChatMessage(ChatRole.Assistant, "我到了。"),
        ChatMessage(ChatRole.User, "开始吧。")
    )

    @Test
    fun compose_wrapsHistoryWithSystemPromptPersonaAndTailInstruction() {
        val result = composer.compose(
            characterSnapshot = snapshot,
            userPersona = persona,
            conversationMessages = history
        )

        assertEquals(ChatRole.System, result.first().role)
        assertEquals(ChatRole.System, result.last().role)
        assertTrue(result[0].content.contains("Alice"))
        assertTrue(result[0].content.contains("长期连续对话"))
        assertTrue(result.any { it.role == ChatRole.System && it.content.contains("用户 Persona") })
        assertTrue(result.any { it.role == ChatRole.System && it.content.contains("继续调查") })
        // 对话消息应出现在系统消息之后和最后一条 System 消息之前
        val assistantIndex = result.indexOfFirst { it.role == ChatRole.Assistant && it.content == "我到了。" }
        val userIndex = result.indexOfFirst { it.role == ChatRole.User && it.content == "开始吧。" }
        assertTrue(assistantIndex > 0)
        assertTrue(userIndex == assistantIndex + 1)
        assertEquals(ChatRole.System, result.last().role)
    }

    @Test
    fun compose_withDirectorGuidance_insertsGuidanceBeforePostHistory() {
        val guidance = DirectorGuidance(
            mood = "温暖",
            topicDirection = "聊案件进展",
            avoid = "争吵",
            pursue = "信任建立"
        )
        val result = composer.compose(
            characterSnapshot = snapshot,
            userPersona = persona,
            conversationMessages = history,
            directorGuidance = guidance
        )

        val guidanceIndex = result.indexOfFirst {
            it.role == ChatRole.System && it.content.contains("导演指示")
        }
        val postHistoryIndex = result.indexOfFirst {
            it.role == ChatRole.System && it.content.contains("只输出 Alice 的回复")
        }
        assertTrue("guidance should appear before postHistory", guidanceIndex < postHistoryIndex)
        assertTrue(result[guidanceIndex].content.contains("温暖"))
        assertTrue(result[guidanceIndex].content.contains("聊案件进展"))
    }

    @Test
    fun compose_withProactiveInstruction_insertsSystemBlockBeforePostHistory() {
        val instruction = ProactiveInstruction(
            action = ProactiveAction.START_NEW_TOPIC,
            timeCue = "有一阵子没联系了"
        )
        val result = composer.compose(
            characterSnapshot = snapshot,
            userPersona = persona,
            conversationMessages = history,
            proactiveInstruction = instruction
        )

        val proactiveIndex = result.indexOfFirst {
            it.role == ChatRole.System && it.content.contains("主动消息指令")
        }
        val postHistoryIndex = result.indexOfFirst {
            it.role == ChatRole.System && it.content.contains("只输出 Alice 的回复")
        }
        assertTrue(proactiveIndex in 0 until postHistoryIndex)
        assertTrue(result[proactiveIndex].content.contains("当前没有来自 Bob 的新消息"))
        assertTrue(result[proactiveIndex].content.contains("主动开启一个新话题"))
        assertTrue(result[proactiveIndex].content.contains("有一阵子没联系了"))
    }

    @Test
    fun compose_doesNotIncludeEmotionBlock() {
        val result = composer.compose(
            characterSnapshot = snapshot,
            userPersona = persona,
            conversationMessages = history
        )

        assertFalse(result.any { it.role == ChatRole.System && it.content.contains("好感度") })
        assertFalse(result.any { it.role == ChatRole.System && it.content.contains("心情") })
    }

    @Test
    fun compose_withNullGuidanceAndNullProactiveInstruction_producesNoDirectorOrProactiveMessages() {
        val result = composer.compose(
            characterSnapshot = snapshot,
            userPersona = persona,
            conversationMessages = history,
            directorGuidance = null,
            proactiveInstruction = null
        )

        assertFalse(result.any { it.content.contains("导演指示") })
        assertFalse(result.any { it.content.contains("主动消息指令") })
    }
}
