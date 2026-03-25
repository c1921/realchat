package io.github.c1921.realchat.ui

import io.github.c1921.realchat.data.chat.ChatHistoryRepository
import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.data.chat.PersistedChatState
import io.github.c1921.realchat.data.settings.SettingsRepository
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.ProviderConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_restoresPersistedMessagesAndDraft() = runTest(mainDispatcherRule.dispatcher) {
        val persistedMessages = listOf(
            ChatMessage(ChatRole.User, "你好"),
            ChatMessage(ChatRole.Assistant, "世界")
        )
        val historyRepository = FakeChatHistoryRepository(
            PersistedChatState(
                messages = persistedMessages,
                draft = "未发送草稿"
            )
        )
        val viewModel = ChatViewModel(
            settingsRepository = FakeSettingsRepository(validConfig()),
            chatProvider = FakeChatProvider(),
            chatHistoryRepository = historyRepository
        )

        advanceUntilIdle()

        assertEquals(persistedMessages, viewModel.uiState.value.chat.messages)
        assertEquals("未发送草稿", viewModel.uiState.value.chat.draft)
        assertFalse(viewModel.uiState.value.chat.isSending)
        assertNull(viewModel.uiState.value.chat.errorText)
    }

    @Test
    fun sendMessage_persistsExchangeAndClearsDraftOnSuccess() = runTest(mainDispatcherRule.dispatcher) {
        val provider = FakeChatProvider { _, _ ->
            Result.success(ChatMessage(ChatRole.Assistant, "收到"))
        }
        val historyRepository = FakeChatHistoryRepository()
        val viewModel = ChatViewModel(
            settingsRepository = FakeSettingsRepository(validConfig()),
            chatProvider = provider,
            chatHistoryRepository = historyRepository
        )

        advanceUntilIdle()
        viewModel.updateDraft("你好")
        advanceUntilIdle()

        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(
            listOf(ChatMessage(ChatRole.User, "你好")),
            provider.requests.single().messages
        )
        assertEquals(1, historyRepository.appendedExchanges.size)
        assertEquals(
            listOf(
                ChatMessage(ChatRole.User, "你好"),
                ChatMessage(ChatRole.Assistant, "收到")
            ),
            viewModel.uiState.value.chat.messages
        )
        assertEquals("", viewModel.uiState.value.chat.draft)
        assertFalse(viewModel.uiState.value.chat.isSending)
        assertNull(viewModel.uiState.value.chat.errorText)
    }

    @Test
    fun sendMessage_keepsDraftWhenRequestFails() = runTest(mainDispatcherRule.dispatcher) {
        val provider = FakeChatProvider { _, _ ->
            Result.failure(IOException("网络错误"))
        }
        val historyRepository = FakeChatHistoryRepository()
        val viewModel = ChatViewModel(
            settingsRepository = FakeSettingsRepository(validConfig()),
            chatProvider = provider,
            chatHistoryRepository = historyRepository
        )

        advanceUntilIdle()
        viewModel.updateDraft("你好")
        advanceUntilIdle()

        viewModel.sendMessage()
        advanceUntilIdle()

        assertTrue(historyRepository.appendedExchanges.isEmpty())
        assertTrue(viewModel.uiState.value.chat.messages.isEmpty())
        assertEquals("你好", viewModel.uiState.value.chat.draft)
        assertEquals("网络错误", viewModel.uiState.value.chat.errorText)
        assertFalse(viewModel.uiState.value.chat.isSending)
    }

    @Test
    fun sendMessage_withoutValidConfigDoesNotCallProvider() = runTest(mainDispatcherRule.dispatcher) {
        val provider = FakeChatProvider()
        val historyRepository = FakeChatHistoryRepository()
        val viewModel = ChatViewModel(
            settingsRepository = FakeSettingsRepository(),
            chatProvider = provider,
            chatHistoryRepository = historyRepository
        )

        advanceUntilIdle()
        viewModel.updateDraft("你好")
        advanceUntilIdle()

        viewModel.sendMessage()
        advanceUntilIdle()

        assertTrue(provider.requests.isEmpty())
        assertTrue(historyRepository.appendedExchanges.isEmpty())
        assertEquals(
            "请先在设置中保存 API Key、模型和 Base URL。",
            viewModel.uiState.value.chat.errorText
        )
        assertFalse(viewModel.uiState.value.chat.hasValidConfig)
    }
}

private data class ChatRequest(
    val messages: List<ChatMessage>,
    val config: ProviderConfig
)

private class FakeChatProvider(
    private val handler: suspend (List<ChatMessage>, ProviderConfig) -> Result<ChatMessage> = { _, _ ->
        Result.failure(AssertionError("不应发起请求"))
    }
) : ChatProvider {
    val requests = mutableListOf<ChatRequest>()

    override suspend fun send(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<ChatMessage> {
        requests += ChatRequest(messages = messages, config = config)
        return handler(messages, config)
    }
}

private class FakeSettingsRepository(
    initialConfig: ProviderConfig = ProviderConfig()
) : SettingsRepository {
    private val configs = MutableStateFlow(initialConfig)

    override fun observeConfig(): Flow<ProviderConfig> = configs

    override suspend fun saveConfig(config: ProviderConfig) {
        configs.value = config
    }
}

private class FakeChatHistoryRepository(
    initialState: PersistedChatState = PersistedChatState()
) : ChatHistoryRepository {
    private val state = MutableStateFlow(initialState)

    val updatedDrafts = mutableListOf<String>()
    val appendedExchanges = mutableListOf<Pair<String, ChatMessage>>()

    override fun observeState(): Flow<PersistedChatState> = state

    override suspend fun updateDraft(draft: String) {
        updatedDrafts += draft
        state.value = state.value.copy(draft = draft)
    }

    override suspend fun appendSuccessfulExchange(
        userContent: String,
        assistantMessage: ChatMessage
    ) {
        appendedExchanges += userContent to assistantMessage
        state.value = PersistedChatState(
            messages = state.value.messages +
                ChatMessage(ChatRole.User, userContent) +
                assistantMessage,
            draft = ""
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private fun validConfig(): ProviderConfig {
    return ProviderConfig(
        apiKey = "test-key",
        model = "deepseek-chat",
        baseUrl = "https://api.deepseek.com"
    )
}
