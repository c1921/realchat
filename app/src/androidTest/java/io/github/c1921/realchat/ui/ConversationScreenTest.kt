package io.github.c1921.realchat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.Conversation
import io.github.c1921.realchat.model.ConversationListItem
import org.junit.Rule
import org.junit.Test

class ConversationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatDetailScreen_rendersChatStyleComponents() {
        composeRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    conversation = sampleConversationState(draft = "在吗"),
                    settings = SettingsUiState(),
                    onGetProactiveNextTriggerMs = { 0L },
                    onGetProactiveSentCount = { 0 },
                    onBack = { },
                    onDraftChange = { },
                    onSendMessage = { }
                )
            }
        }

        composeRule.onNodeWithTag("message_list").assertExists()
        composeRule.onNodeWithTag("message_bubble_assistant").assertExists()
        composeRule.onNodeWithTag("message_bubble_user").assertExists()
        composeRule.onNodeWithTag("message_banner_system").assertExists()
        composeRule.onNodeWithTag("chat_composer_input").assertExists()
        composeRule.onNodeWithTag("chat_composer_send").assertExists().assertIsEnabled()
        composeRule.onAllNodesWithText("小雨").assertCountEquals(1)
        composeRule.onNodeWithText("夜聊").assertDoesNotExist()
    }

    @Test
    fun chatDetailScreen_showsDirectorGuidanceHintInDeveloperMode() {
        val hint = "导演指示：氛围：温暖，推进：保持亲近"

        composeRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    conversation = sampleConversationState(
                        directorGuidanceHints = mapOf(0 to hint)
                    ),
                    settings = SettingsUiState(developerModeEnabled = true),
                    onGetProactiveNextTriggerMs = { 0L },
                    onGetProactiveSentCount = { 0 },
                    onBack = { },
                    onDraftChange = { },
                    onSendMessage = { }
                )
            }
        }

        composeRule.onNodeWithText(hint).assertExists()
    }

    @Test
    fun chatDetailScreen_hidesDirectorGuidanceHintWhenDeveloperModeDisabled() {
        val hint = "导演指示：氛围：温暖，推进：保持亲近"

        composeRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    conversation = sampleConversationState(
                        directorGuidanceHints = mapOf(0 to hint)
                    ),
                    settings = SettingsUiState(),
                    onGetProactiveNextTriggerMs = { 0L },
                    onGetProactiveSentCount = { 0 },
                    onBack = { },
                    onDraftChange = { },
                    onSendMessage = { }
                )
            }
        }

        composeRule.onNodeWithText(hint).assertDoesNotExist()
    }

    @Test
    fun chatDetailScreen_ignoresMismatchedDirectorGuidanceIndex() {
        val hint = "导演指示：不会显示"

        composeRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    conversation = sampleConversationState(
                        directorGuidanceHints = mapOf(1 to hint)
                    ),
                    settings = SettingsUiState(developerModeEnabled = true),
                    onGetProactiveNextTriggerMs = { 0L },
                    onGetProactiveSentCount = { 0 },
                    onBack = { },
                    onDraftChange = { },
                    onSendMessage = { }
                )
            }
        }

        composeRule.onNodeWithText(hint).assertDoesNotExist()
    }

    @Test
    fun conversationHomeScreen_rendersConversationRowsAndActions() {
        composeRule.setContent {
            MaterialTheme {
                ConversationHomeScreen(
                    conversation = sampleConversationState(),
                    cards = emptyList(),
                    onOpenConversationDetail = { },
                    onShowCreateConversationDialog = { },
                    onDismissCreateConversationDialog = { },
                    onPendingConversationCardIdChange = { },
                    onCreateConversation = { },
                    onDeleteSelectedConversation = { }
                )
            }
        }

        composeRule.onNodeWithTag("conversation_list").assertExists()
        composeRule.onNodeWithTag("conversation_row_1").assertExists()
        composeRule.onNodeWithText("小雨").assertExists()
        composeRule.onNodeWithText("角色：你好呀").assertExists()
        composeRule.onNodeWithContentDescription("新建会话").assertExists()
        composeRule.onNodeWithContentDescription("删除会话").assertExists()
    }

    private fun sampleConversationState(
        draft: String = "",
        directorGuidanceHints: Map<Int, String> = emptyMap()
    ): ConversationUiState {
        val conversation = Conversation(
            id = 1L,
            characterSnapshot = CharacterCardSnapshot(name = "小雨")
        )
        return ConversationUiState(
            conversationItems = listOf(
                ConversationListItem(
                    conversation = conversation,
                    latestMessage = ChatMessage(ChatRole.Assistant, "你好呀")
                )
            ),
            selectedConversationId = 1L,
            messages = listOf(
                ChatMessage(ChatRole.Assistant, "你好呀"),
                ChatMessage(ChatRole.User, "在吗"),
                ChatMessage(ChatRole.System, "系统通知")
            ),
            directorGuidanceHints = directorGuidanceHints,
            draft = draft,
            hasValidConfig = true
        )
    }
}
