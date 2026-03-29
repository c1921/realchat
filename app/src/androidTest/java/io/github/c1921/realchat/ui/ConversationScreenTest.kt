package io.github.c1921.realchat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.Conversation
import io.github.c1921.realchat.model.ConversationDebugEvent
import io.github.c1921.realchat.model.ConversationDebugSource
import io.github.c1921.realchat.model.ConversationDebugType
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
    fun chatDetailScreen_showsDebugEventInDeveloperMode() {
        val summary = "导演分析完成"

        composeRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    conversation = sampleConversationState(
                        debugEvents = listOf(
                            ConversationDebugEvent(
                                id = 1L,
                                conversationId = 1L,
                                source = ConversationDebugSource.Director,
                                type = ConversationDebugType.DirectorAnalysisSucceeded,
                                title = "导演分析完成",
                                summary = summary,
                                details = "原始输出\n{\"mood\":\"温暖\"}",
                                createdAt = 3L
                            )
                        )
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

        composeRule.onNodeWithText(summary).assertExists()
        composeRule.onNodeWithTag("debug_event_card").assertExists()
    }

    @Test
    fun chatDetailScreen_hidesDebugEventWhenDeveloperModeDisabled() {
        val summary = "导演分析完成"

        composeRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    conversation = sampleConversationState(
                        debugEvents = listOf(
                            ConversationDebugEvent(
                                id = 1L,
                                conversationId = 1L,
                                source = ConversationDebugSource.Director,
                                type = ConversationDebugType.DirectorAnalysisSucceeded,
                                title = "导演分析完成",
                                summary = summary,
                                details = "原始输出\n{\"mood\":\"温暖\"}",
                                createdAt = 3L
                            )
                        )
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

        composeRule.onNodeWithText(summary).assertDoesNotExist()
    }

    @Test
    fun chatDetailScreen_expandsDebugEventDetails() {
        val summary = "导演分析完成"

        composeRule.setContent {
            MaterialTheme {
                ChatDetailScreen(
                    conversation = sampleConversationState(
                        debugEvents = listOf(
                            ConversationDebugEvent(
                                id = 1L,
                                conversationId = 1L,
                                source = ConversationDebugSource.Director,
                                type = ConversationDebugType.DirectorAnalysisSucceeded,
                                title = "导演分析完成",
                                summary = summary,
                                details = "原始输出\n{\"mood\":\"温暖\"}",
                                createdAt = 3L
                            )
                        )
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

        composeRule.onNodeWithTag("debug_event_details").assertDoesNotExist()
        composeRule.onNodeWithText("展开详情").performClick()
        composeRule.onNodeWithTag("debug_event_details").assertExists()
        composeRule.onNodeWithText("原始输出").assertExists()
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
        debugEvents: List<ConversationDebugEvent> = emptyList()
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
            debugEvents = debugEvents,
            draft = draft,
            hasValidConfig = true
        )
    }
}
