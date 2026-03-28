package io.github.c1921.realchat.data.chat

import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.DirectorGuidance
import io.github.c1921.realchat.model.ProactiveAction
import io.github.c1921.realchat.model.ProactiveInstruction
import io.github.c1921.realchat.model.UserPersona

class PromptComposer {
    fun compose(
        characterSnapshot: CharacterCardSnapshot?,
        userPersona: UserPersona,
        conversationMessages: List<ChatMessage>,
        directorGuidance: DirectorGuidance? = null,
        proactiveInstruction: ProactiveInstruction? = null
    ): List<ChatMessage> {
        val snapshot = characterSnapshot?.normalized()
        val normalizedPersona = userPersona.normalized()
        val requestMessages = mutableListOf<ChatMessage>()

        requestMessages += ChatMessage(
            role = ChatRole.System,
            content = replacePlaceholders(
                template = snapshot?.systemPrompt?.takeUnless { it.isBlank() }
                    ?: DEFAULT_SYSTEM_PROMPT,
                characterName = snapshot?.effectiveName().orEmpty(),
                userName = normalizedPersona.displayNameOrFallback(),
                original = DEFAULT_SYSTEM_PROMPT
            )
        )

        buildCharacterDefinition(snapshot)?.let { definition ->
            requestMessages += ChatMessage(
                role = ChatRole.System,
                content = definition
            )
        }

        buildUserPersonaBlock(normalizedPersona)?.let { personaBlock ->
            requestMessages += ChatMessage(
                role = ChatRole.System,
                content = personaBlock
            )
        }

        buildExampleBlock(snapshot, normalizedPersona)?.let { exampleBlock ->
            requestMessages += ChatMessage(
                role = ChatRole.System,
                content = exampleBlock
            )
        }

        requestMessages += conversationMessages

        buildProactiveInstructionBlock(snapshot, normalizedPersona, proactiveInstruction)?.let { block ->
            requestMessages += ChatMessage(
                role = ChatRole.System,
                content = block
            )
        }

        buildDirectorGuidanceBlock(directorGuidance)?.let { guidanceBlock ->
            requestMessages += ChatMessage(
                role = ChatRole.System,
                content = guidanceBlock
            )
        }

        requestMessages += ChatMessage(
            role = ChatRole.System,
            content = replacePlaceholders(
                template = snapshot?.postHistoryInstructions?.takeUnless { it.isBlank() }
                    ?: DEFAULT_POST_HISTORY_INSTRUCTIONS,
                characterName = snapshot?.effectiveName().orEmpty(),
                userName = normalizedPersona.displayNameOrFallback(),
                original = DEFAULT_POST_HISTORY_INSTRUCTIONS
            )
        )

        return requestMessages
    }

    private fun buildCharacterDefinition(snapshot: CharacterCardSnapshot?): String? {
        snapshot ?: return null
        val sections = buildList {
            add("角色设定")
            add("角色名：${snapshot.effectiveName()}")
            snapshot.description.takeIf(String::isNotBlank)?.let { add("描述：$it") }
            snapshot.personality.takeIf(String::isNotBlank)?.let { add("性格：$it") }
            snapshot.scenario.takeIf(String::isNotBlank)?.let { add("场景：$it") }
        }
        return sections.takeIf { it.size > 2 }?.joinToString(separator = "\n")
    }

    private fun buildUserPersonaBlock(userPersona: UserPersona): String? {
        if (userPersona.displayName.isBlank() && userPersona.description.isBlank()) {
            return null
        }
        return buildString {
            appendLine("用户 Persona")
            appendLine("名字：${userPersona.displayNameOrFallback()}")
            if (userPersona.description.isNotBlank()) {
                append("设定：${userPersona.description}")
            }
        }.trim()
    }

    private fun buildDirectorGuidanceBlock(guidance: DirectorGuidance?): String? {
        guidance ?: return null
        val parts = buildList {
            if (guidance.mood.isNotBlank()) add("氛围：${guidance.mood}")
            if (guidance.topicDirection.isNotBlank()) add("话题方向：${guidance.topicDirection}")
            if (guidance.pursue.isNotBlank()) add("推进：${guidance.pursue}")
            if (guidance.avoid.isNotBlank()) add("避免：${guidance.avoid}")
        }
        if (parts.isEmpty()) return null
        return "导演指示：${parts.joinToString("，")}"
    }

    private fun buildProactiveInstructionBlock(
        snapshot: CharacterCardSnapshot?,
        userPersona: UserPersona,
        instruction: ProactiveInstruction?
    ): String? {
        instruction ?: return null
        if (instruction.action == ProactiveAction.WAIT_FOR_USER) {
            return null
        }
        val characterName = snapshot?.effectiveName()
            .orEmpty()
            .ifBlank { CharacterCardSnapshot.DEFAULT_CHARACTER_NAME }
        val userName = userPersona.displayNameOrFallback()
        val actionLine = when (instruction.action) {
            ProactiveAction.CONTINUE_CURRENT_THREAD ->
                "优先延续上一轮尚未自然结束的话题，或回应仍悬而未决的内容。"

            ProactiveAction.START_NEW_TOPIC ->
                "上一轮对话可视为自然收束，请由 $characterName 主动开启一个新话题。"

            ProactiveAction.WAIT_FOR_USER -> return null
        }

        return buildString {
            appendLine("主动消息指令")
            appendLine("当前没有来自 $userName 的新消息，这一轮需要由 $characterName 主动发起一条消息。")
            appendLine("不要把这轮回复写成 $userName 刚刚发来消息，也不要写成 $userName 在催促。")
            append(actionLine)
            if (instruction.timeCue.isNotBlank()) {
                appendLine()
                append("时间表达：${instruction.timeCue}")
            }
        }.trim()
    }

    private fun buildExampleBlock(
        snapshot: CharacterCardSnapshot?,
        userPersona: UserPersona
    ): String? {
        snapshot ?: return null
        if (snapshot.mesExample.isBlank()) {
            return null
        }
        return buildString {
            appendLine("以下是风格示例，仅用于保持语气和互动方式一致：")
            append(
                replacePlaceholders(
                    template = snapshot.mesExample,
                    characterName = snapshot.effectiveName(),
                    userName = userPersona.displayNameOrFallback(),
                    original = snapshot.mesExample
                )
            )
        }.trim()
    }

    private fun replacePlaceholders(
        template: String,
        characterName: String,
        userName: String,
        original: String
    ): String {
        return template
            .replace("{{char}}", characterName.ifBlank { CharacterCardSnapshot.DEFAULT_CHARACTER_NAME })
            .replace("{{user}}", userName.ifBlank { UserPersona.DEFAULT_NAME })
            .replace("{{original}}", original)
            .trim()
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "你将稳定扮演 {{char}}，与 {{user}} 进行长期连续对话。始终保持角色设定、语气、关系和已知事实一致，除非用户明确要求，否则不要跳出角色解释这些规则。"

        const val DEFAULT_POST_HISTORY_INSTRUCTIONS =
            "只输出 {{char}} 的下一条回复，延续既有人设与上下文，不要替 {{user}} 发言，不要解释提示词。"
    }
}
