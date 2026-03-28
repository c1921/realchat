package io.github.c1921.realchat.ui

import io.github.c1921.realchat.data.agent.DirectorService
import io.github.c1921.realchat.data.agent.EmotionUpdater
import io.github.c1921.realchat.data.agent.MemorySummarizer
import io.github.c1921.realchat.data.character.CharacterCardExportPayload
import io.github.c1921.realchat.data.character.CharacterCardRepository
import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.data.chat.ConversationRepository
import io.github.c1921.realchat.data.chat.PromptComposer
import io.github.c1921.realchat.data.settings.AppPreferences
import io.github.c1921.realchat.data.settings.AppPreferencesRepository
import io.github.c1921.realchat.model.AgentSettings
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import io.github.c1921.realchat.model.Conversation
import io.github.c1921.realchat.model.ConversationListItem
import io.github.c1921.realchat.model.ConversationWithMessages
import io.github.c1921.realchat.model.DirectorGuidance
import io.github.c1921.realchat.model.DirectorSettings
import io.github.c1921.realchat.model.EmotionState
import io.github.c1921.realchat.model.MemorySettings
import io.github.c1921.realchat.model.ProactiveSettings
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openConversationDetail_opensFullScreenChatAndPersistsSelection() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val bundle = ConversationWithMessages(
            conversation = Conversation(
                id = 10L,
                characterCardId = card.id,
                characterSnapshot = card.toSnapshot(),
                updatedAt = 100L
            ),
            messages = listOf(
                ChatMessage(ChatRole.Assistant, "你好")
            )
        )
        val preferencesRepository = FakeAppPreferencesRepository()
        val viewModel = ChatViewModel(
            appPreferencesRepository = preferencesRepository,
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(listOf(bundle)),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = FakeDirectorService(),
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        viewModel.openConversationDetail(10L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AppScreen.Conversations, state.currentScreen)
        assertEquals(SecondaryScreen.ChatDetail(10L), state.secondaryScreen)
        assertEquals(10L, state.conversation.selectedConversationId)
        assertEquals(10L, preferencesRepository.preferences.value.selectedConversationId)
        assertEquals("你好", state.conversation.messages.single().content)
    }

    @Test
    fun closeSecondaryScreen_keepsSelectedConversation() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val bundle = ConversationWithMessages(
            conversation = Conversation(
                id = 10L,
                characterCardId = card.id,
                characterSnapshot = card.toSnapshot(),
                updatedAt = 100L
            ),
            messages = listOf(
                ChatMessage(ChatRole.Assistant, "你好")
            )
        )
        val viewModel = ChatViewModel(
            appPreferencesRepository = FakeAppPreferencesRepository(),
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(listOf(bundle)),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = FakeDirectorService(),
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        viewModel.openConversationDetail(10L)
        advanceUntilIdle()
        viewModel.closeSecondaryScreen()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.secondaryScreen)
        assertEquals(10L, state.conversation.selectedConversationId)
        assertEquals("你好", state.conversation.messages.single().content)
    }

    @Test
    fun createConversation_createsConversationForSelectedCard() = runTest {
        val firstCard = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val secondCard = CharacterCard(
            id = 2L,
            name = "Bob",
            firstMes = "第二位角色已就绪。"
        )
        val viewModel = ChatViewModel(
            appPreferencesRepository = FakeAppPreferencesRepository(),
            characterCardRepository = FakeCharacterCardRepository(listOf(firstCard, secondCard)),
            conversationRepository = FakeConversationRepository(emptyList()),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = FakeDirectorService(),
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        viewModel.showCreateConversationDialog()
        viewModel.updatePendingConversationCardId(secondCard.id)
        viewModel.createConversation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.conversation.conversationItems.size)
        assertEquals(2L, state.conversation.selectedConversationId)
        assertEquals("Bob", state.conversation.selectedConversation()?.characterSnapshot?.effectiveName())
        assertEquals("已创建新会话。", state.conversation.statusText)
    }

    @Test
    fun openSettingsDetail_keepsSecondaryScreenUntilClosed() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val viewModel = ChatViewModel(
            appPreferencesRepository = FakeAppPreferencesRepository(),
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(emptyList()),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = FakeDirectorService(),
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        viewModel.openSettingsDetail(SettingsSection.Proactive)
        advanceUntilIdle()

        assertEquals(
            SecondaryScreen.SettingsDetail(SettingsSection.Proactive),
            viewModel.uiState.value.secondaryScreen
        )

        viewModel.closeSecondaryScreen()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.secondaryScreen)
    }

    @Test
    fun applyProviderSettings_persistsSelectedProviderAndConfigs() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val preferencesRepository = FakeAppPreferencesRepository()
        val viewModel = ChatViewModel(
            appPreferencesRepository = preferencesRepository,
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(emptyList()),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = FakeDirectorService(),
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        val providerConfigs = preferencesRepository.preferences.value.providerConfigs.toMutableMap().apply {
            this[ProviderType.DEEPSEEK] = getValue(ProviderType.DEEPSEEK).copy(apiKey = "deepseek-key")
            this[ProviderType.OPENAI] = getValue(ProviderType.OPENAI).copy(
                apiKey = "openai-key",
                model = "gpt-4o-mini"
            )
        }

        val error = viewModel.applyProviderSettings(
            selectedProviderType = ProviderType.OPENAI,
            providerConfigs = providerConfigs
        )
        advanceUntilIdle()

        assertNull(error)
        assertEquals(ProviderType.OPENAI, preferencesRepository.preferences.value.selectedProviderType)
        assertEquals(ProviderType.OPENAI, viewModel.uiState.value.settings.providerType)
        assertEquals("https://api.openai.com/v1", preferencesRepository.preferences.value.providerConfig.baseUrl)
        assertEquals(
            "deepseek-key",
            preferencesRepository.preferences.value.providerConfigs
                .getValue(ProviderType.DEEPSEEK)
                .apiKey
        )
        assertEquals(
            "openai-key",
            preferencesRepository.preferences.value.providerConfigs
                .getValue(ProviderType.OPENAI)
                .apiKey
        )
        assertEquals(
            "gpt-4o-mini",
            viewModel.uiState.value.settings.providerConfigs
                .getValue(ProviderType.OPENAI)
                .model
        )
    }

    @Test
    fun applyProviderSettings_rejectsInvalidBaseUrl() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val preferencesRepository = FakeAppPreferencesRepository()
        val viewModel = ChatViewModel(
            appPreferencesRepository = preferencesRepository,
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(emptyList()),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = FakeDirectorService(),
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        val invalidConfigs = preferencesRepository.preferences.value.providerConfigs.toMutableMap().apply {
            this[ProviderType.OPENAI] = getValue(ProviderType.OPENAI).copy(baseUrl = "not-a-url")
        }

        val error = viewModel.applyProviderSettings(
            selectedProviderType = ProviderType.OPENAI,
            providerConfigs = invalidConfigs
        )
        advanceUntilIdle()

        assertEquals("Base URL 格式不正确。", error)
        assertEquals(ProviderType.DEEPSEEK, preferencesRepository.preferences.value.selectedProviderType)
        assertEquals("Base URL 格式不正确。", viewModel.uiState.value.settings.errorText)
    }

    @Test
    fun applyProactiveSettings_updatesUiAndRepository() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val preferencesRepository = FakeAppPreferencesRepository()
        val viewModel = ChatViewModel(
            appPreferencesRepository = preferencesRepository,
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(emptyList()),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = FakeDirectorService(),
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        val error = viewModel.applyProactiveSettings(
            ProactiveSettings(
                enabled = true,
                minIntervalMinutes = 45,
                maxIntervalMinutes = 90,
                maxCount = 3
            )
        )
        advanceUntilIdle()

        assertNull(error)
        assertEquals(45, preferencesRepository.preferences.value.agentSettings.proactive.minIntervalMinutes)
        assertEquals(90, viewModel.uiState.value.settings.proactiveMaxIntervalMinutes)
        assertTrue(viewModel.uiState.value.settings.proactiveEnabled)
    }

    @Test
    fun applyMemorySettings_rejectsInvalidThreshold() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val preferencesRepository = FakeAppPreferencesRepository()
        val viewModel = ChatViewModel(
            appPreferencesRepository = preferencesRepository,
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(emptyList()),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = FakeDirectorService(),
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        val error = viewModel.applyMemorySettings(
            MemorySettings(
                enabled = true,
                triggerCount = 0,
                keepRecentCount = 10
            )
        )
        advanceUntilIdle()

        assertEquals("记忆摘要触发阈值不能小于 1。", error)
        assertEquals(40, preferencesRepository.preferences.value.agentSettings.memory.triggerCount)
    }

    @Test
    fun sendMessage_recordsStructuredDirectorGuidanceHint() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val bundle = ConversationWithMessages(
            conversation = Conversation(
                id = 10L,
                characterCardId = card.id,
                characterSnapshot = card.toSnapshot(),
                updatedAt = 100L
            ),
            messages = listOf(
                ChatMessage(ChatRole.Assistant, "你好")
            )
        )
        val preferencesRepository = FakeAppPreferencesRepository().also { repository ->
            repository.preferences.update { current ->
                current.copy(
                    agentSettings = current.agentSettings.copy(
                        director = DirectorSettings(enabled = true)
                    )
                )
            }
        }
        val directorService = FakeDirectorService(
            Result.success(
                DirectorGuidance(
                    mood = "温暖",
                    topicDirection = "聊案件",
                    avoid = "争吵",
                    pursue = "建立信任"
                )
            )
        )
        val viewModel = ChatViewModel(
            appPreferencesRepository = preferencesRepository,
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(listOf(bundle)),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = directorService,
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        viewModel.updateDraft("继续")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, directorService.callCount)
        assertEquals(
            mapOf(2 to "导演指示：氛围：温暖，话题方向：聊案件，推进：建立信任，避免：争吵"),
            viewModel.uiState.value.conversation.directorGuidanceHints
        )
    }

    @Test
    fun sendMessage_fallsBackToRawDirectorOutputHint() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val bundle = ConversationWithMessages(
            conversation = Conversation(
                id = 10L,
                characterCardId = card.id,
                characterSnapshot = card.toSnapshot(),
                updatedAt = 100L
            ),
            messages = listOf(
                ChatMessage(ChatRole.Assistant, "你好")
            )
        )
        val preferencesRepository = FakeAppPreferencesRepository().also { repository ->
            repository.preferences.update { current ->
                current.copy(
                    agentSettings = current.agentSettings.copy(
                        director = DirectorSettings(enabled = true)
                    )
                )
            }
        }
        val directorService = FakeDirectorService(
            Result.success(DirectorGuidance(rawJson = "  {\"topic\":\"keep going\"}  "))
        )
        val viewModel = ChatViewModel(
            appPreferencesRepository = preferencesRepository,
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(listOf(bundle)),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = directorService,
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        viewModel.updateDraft("继续")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, directorService.callCount)
        assertEquals(
            mapOf(2 to "导演原始输出：{\"topic\":\"keep going\"}"),
            viewModel.uiState.value.conversation.directorGuidanceHints
        )
    }

    @Test
    fun sendMessage_skipsDirectorHintWhenGuidanceIsEmpty() = runTest {
        val card = CharacterCard(
            id = 1L,
            name = "Alice"
        )
        val bundle = ConversationWithMessages(
            conversation = Conversation(
                id = 10L,
                characterCardId = card.id,
                characterSnapshot = card.toSnapshot(),
                updatedAt = 100L
            ),
            messages = listOf(
                ChatMessage(ChatRole.Assistant, "你好")
            )
        )
        val preferencesRepository = FakeAppPreferencesRepository().also { repository ->
            repository.preferences.update { current ->
                current.copy(
                    agentSettings = current.agentSettings.copy(
                        director = DirectorSettings(enabled = true)
                    )
                )
            }
        }
        val directorService = FakeDirectorService(Result.success(DirectorGuidance()))
        val viewModel = ChatViewModel(
            appPreferencesRepository = preferencesRepository,
            characterCardRepository = FakeCharacterCardRepository(listOf(card)),
            conversationRepository = FakeConversationRepository(listOf(bundle)),
            chatProvider = FakeChatProvider(),
            promptComposer = PromptComposer(),
            directorService = directorService,
            emotionUpdater = FakeEmotionUpdater(),
            memorySummarizer = FakeMemorySummarizer()
        )

        advanceUntilIdle()
        viewModel.updateDraft("继续")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, directorService.callCount)
        assertTrue(viewModel.uiState.value.conversation.directorGuidanceHints.isEmpty())
    }
}

private class FakeAppPreferencesRepository(
    initial: AppPreferences = AppPreferences(
        selectedProviderType = ProviderType.DEEPSEEK,
        providerConfigs = ProviderConfig.defaultsByProvider().toMutableMap().apply {
            this[ProviderType.DEEPSEEK] = ProviderConfig(
                providerType = ProviderType.DEEPSEEK,
                apiKey = "test-key",
                model = ProviderConfig.DEFAULT_MODEL,
                baseUrl = ProviderConfig.DEFAULT_BASE_URL
            )
            this[ProviderType.OPENAI] = ProviderConfig.defaultsFor(ProviderType.OPENAI)
            this[ProviderType.OPENAI_COMPATIBLE] =
                ProviderConfig.defaultsFor(ProviderType.OPENAI_COMPATIBLE)
        }
    )
) : AppPreferencesRepository {
    val preferences = MutableStateFlow(initial)

    override fun observePreferences(): Flow<AppPreferences> = preferences

    override suspend fun saveProviderSettings(
        selectedProviderType: ProviderType,
        providerConfigs: Map<ProviderType, ProviderConfig>
    ) {
        preferences.update { current ->
            current.copy(
                selectedProviderType = selectedProviderType,
                providerConfigs = providerConfigs
            )
        }
    }

    override suspend fun saveUserPersona(userPersona: io.github.c1921.realchat.model.UserPersona) {
        preferences.update { current -> current.copy(userPersona = userPersona) }
    }

    override suspend fun saveSelectedConversationId(conversationId: Long?) {
        preferences.update { current -> current.copy(selectedConversationId = conversationId) }
    }

    override suspend fun saveAgentSettings(agentSettings: AgentSettings) {
        preferences.update { current -> current.copy(agentSettings = agentSettings) }
    }

    override suspend fun saveDeveloperMode(enabled: Boolean) {
        preferences.update { current -> current.copy(developerModeEnabled = enabled) }
    }
}

private class FakeCharacterCardRepository(
    initialCards: List<CharacterCard>
) : CharacterCardRepository {
    private val cards = MutableStateFlow(initialCards)

    override fun observeCards(): Flow<List<CharacterCard>> = cards

    override suspend fun getCardById(id: Long): CharacterCard? {
        return cards.value.firstOrNull { it.id == id }
    }

    override suspend fun saveCard(card: CharacterCard): Long {
        val saved = if (card.id == 0L) {
            card.copy(id = (cards.value.maxOfOrNull(CharacterCard::id) ?: 0L) + 1L)
        } else {
            card
        }
        cards.update { current ->
            current.filterNot { it.id == saved.id } + saved
        }
        return saved.id
    }

    override suspend fun duplicateCard(id: Long): Long? = null

    override suspend fun deleteCard(id: Long) {
        cards.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun importCard(jsonText: String): Long {
        error("Not implemented in fake repository")
    }

    override suspend fun exportCard(id: Long): CharacterCardExportPayload? = null

    override suspend fun ensureSeedCard(): CharacterCard {
        return cards.value.firstOrNull() ?: CharacterCard(id = 1L, name = "通用助手").also { seed ->
            cards.value = listOf(seed)
        }
    }
}

private class FakeConversationRepository(
    initialBundles: List<ConversationWithMessages>
) : ConversationRepository {
    private val bundles = MutableStateFlow(initialBundles)

    override fun observeConversations(): Flow<List<Conversation>> {
        return bundles.map { current -> current.map(ConversationWithMessages::conversation) }
    }

    override fun observeConversationListItems(): Flow<List<ConversationListItem>> {
        return bundles.map { current ->
            current.sortedByDescending { it.conversation.updatedAt }.map { bundle ->
                ConversationListItem(
                    conversation = bundle.conversation,
                    latestMessage = bundle.messages.lastOrNull()
                )
            }
        }
    }

    override fun observeConversationWithMessages(conversationId: Long): Flow<ConversationWithMessages?> {
        return bundles.map { current ->
            current.firstOrNull { it.conversation.id == conversationId }
        }
    }

    override suspend fun getConversationById(conversationId: Long): Conversation? {
        return bundles.value.firstOrNull { it.conversation.id == conversationId }?.conversation
    }

    override suspend fun createConversation(characterCard: CharacterCard): Long {
        val nextId = (bundles.value.maxOfOrNull { it.conversation.id } ?: 0L) + 1L
        val conversation = Conversation(
            id = nextId,
            characterCardId = characterCard.id,
            characterSnapshot = characterCard.toSnapshot(),
            updatedAt = nextId
        )
        val messages = characterCard.firstMes.takeIf(String::isNotBlank)?.let { greeting ->
            listOf(ChatMessage(ChatRole.Assistant, greeting))
        }.orEmpty()
        bundles.update { current ->
            current + ConversationWithMessages(conversation = conversation, messages = messages)
        }
        return nextId
    }

    override suspend fun updateDraft(conversationId: Long, draft: String) {
        bundles.update { current ->
            current.map { bundle ->
                if (bundle.conversation.id == conversationId) {
                    bundle.copy(conversation = bundle.conversation.copy(draft = draft))
                } else {
                    bundle
                }
            }
        }
    }

    override suspend fun appendSuccessfulExchange(
        conversationId: Long,
        userContent: String,
        assistantMessage: ChatMessage
    ) {
        bundles.update { current ->
            current.map { bundle ->
                if (bundle.conversation.id == conversationId) {
                    bundle.copy(
                        conversation = bundle.conversation.copy(
                            draft = "",
                            updatedAt = bundle.conversation.updatedAt + 1L
                        ),
                        messages = bundle.messages + ChatMessage(ChatRole.User, userContent) + assistantMessage
                    )
                } else {
                    bundle
                }
            }
        }
    }

    override suspend fun deleteConversation(conversationId: Long) {
        bundles.update { current ->
            current.filterNot { it.conversation.id == conversationId }
        }
    }

    override suspend fun ensureConversationExists(characterCard: CharacterCard): Long {
        return bundles.value.firstOrNull()?.conversation?.id ?: createConversation(characterCard)
    }

    override suspend fun appendProactiveMessage(conversationId: Long, assistantMessage: ChatMessage) {
        bundles.update { current ->
            current.map { bundle ->
                if (bundle.conversation.id == conversationId) {
                    bundle.copy(
                        conversation = bundle.conversation.copy(
                            updatedAt = bundle.conversation.updatedAt + 1L
                        ),
                        messages = bundle.messages + assistantMessage
                    )
                } else {
                    bundle
                }
            }
        }
    }

    override suspend fun updateEmotionState(conversationId: Long, state: EmotionState) {
        bundles.update { current ->
            current.map { bundle ->
                if (bundle.conversation.id == conversationId) {
                    bundle.copy(conversation = bundle.conversation.copy(emotionState = state))
                } else {
                    bundle
                }
            }
        }
    }

    override suspend fun replaceWithSummary(
        conversationId: Long,
        summaryText: String,
        keepRecentMessages: List<ChatMessage>
    ) {
        bundles.update { current ->
            current.map { bundle ->
                if (bundle.conversation.id == conversationId) {
                    val summaryMessage = ChatMessage(ChatRole.System, "[记忆摘要] $summaryText")
                    bundle.copy(
                        conversation = bundle.conversation.copy(memorySummary = summaryText),
                        messages = listOf(summaryMessage) + keepRecentMessages
                    )
                } else {
                    bundle
                }
            }
        }
    }
}

private class FakeChatProvider : ChatProvider {
    override suspend fun send(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<ChatMessage> {
        return Result.success(ChatMessage(ChatRole.Assistant, "ok"))
    }
}

private class FakeDirectorService(
    private val result: Result<DirectorGuidance> = Result.failure(RuntimeException("director disabled"))
) : DirectorService {
    var callCount = 0

    override suspend fun analyze(
        snapshot: CharacterCardSnapshot?,
        emotionState: EmotionState,
        conversationMessages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<DirectorGuidance> {
        callCount++
        return result
    }

}

private class FakeEmotionUpdater(
    private val result: Result<EmotionState> = Result.success(EmotionState(affection = 60, mood = 1))
) : EmotionUpdater {
    var callCount = 0

    override suspend fun update(
        currentState: EmotionState,
        snapshot: CharacterCardSnapshot?,
        recentMessages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<EmotionState> {
        callCount++
        return result
    }
}

private class FakeMemorySummarizer(
    private val result: Result<String> = Result.success("摘要内容")
) : MemorySummarizer {
    var callCount = 0

    override suspend fun summarize(
        messagesToSummarize: List<ChatMessage>,
        snapshot: CharacterCardSnapshot?,
        config: ProviderConfig
    ): Result<String> {
        callCount++
        return result
    }
}
