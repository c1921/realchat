package io.github.c1921.realchat.ui

import io.github.c1921.realchat.data.character.CharacterCardExportPayload
import io.github.c1921.realchat.data.character.CharacterCardRepository
import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.data.chat.ConversationRepository
import io.github.c1921.realchat.data.chat.PromptComposer
import io.github.c1921.realchat.data.settings.AppPreferences
import io.github.c1921.realchat.data.settings.AppPreferencesRepository
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import io.github.c1921.realchat.model.Conversation
import io.github.c1921.realchat.model.ConversationListItem
import io.github.c1921.realchat.model.ConversationWithMessages
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
            promptComposer = PromptComposer()
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
            promptComposer = PromptComposer()
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
            promptComposer = PromptComposer()
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
    fun updateProviderType_switchesBetweenProviderSpecificDrafts() = runTest {
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
            promptComposer = PromptComposer()
        )

        advanceUntilIdle()
        viewModel.updateApiKey("deepseek-key")
        viewModel.updateProviderType(ProviderType.OPENAI)
        viewModel.updateApiKey("openai-key")
        viewModel.updateModel("gpt-4o-mini")
        viewModel.updateProviderType(ProviderType.DEEPSEEK)
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertEquals(ProviderType.DEEPSEEK, settings.providerType)
        assertEquals("deepseek-key", settings.apiKey)
        assertEquals(ProviderConfig.DEFAULT_MODEL, settings.model)
        assertEquals(ProviderConfig.DEFAULT_BASE_URL, settings.baseUrl)

        viewModel.updateProviderType(ProviderType.OPENAI)
        advanceUntilIdle()

        val openAiSettings = viewModel.uiState.value.settings
        assertEquals(ProviderType.OPENAI, openAiSettings.providerType)
        assertEquals("openai-key", openAiSettings.apiKey)
        assertEquals("gpt-4o-mini", openAiSettings.model)
        assertEquals("https://api.openai.com/v1", openAiSettings.baseUrl)
    }

    @Test
    fun saveSettings_persistsAllProviderDrafts() = runTest {
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
            promptComposer = PromptComposer()
        )

        advanceUntilIdle()
        viewModel.updateApiKey("deepseek-key")
        viewModel.updateProviderType(ProviderType.OPENAI)
        viewModel.updateApiKey("openai-key")
        viewModel.updateModel("gpt-4o-mini")
        viewModel.saveSettings()
        advanceUntilIdle()

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
}

private class FakeChatProvider : ChatProvider {
    override suspend fun send(
        messages: List<ChatMessage>,
        config: ProviderConfig
    ): Result<ChatMessage> {
        return Result.success(ChatMessage(ChatRole.Assistant, "ok"))
    }
}
