package io.github.c1921.realchat.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.realchat.data.character.CharacterCardExportPayload
import io.github.c1921.realchat.data.character.CharacterCardRepository
import io.github.c1921.realchat.data.character.RoomCharacterCardRepository
import io.github.c1921.realchat.data.chat.AppDatabase
import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.data.chat.ConversationRepository
import io.github.c1921.realchat.data.chat.DeepSeekChatProvider
import io.github.c1921.realchat.data.chat.PromptComposer
import io.github.c1921.realchat.data.chat.RoomConversationRepository
import io.github.c1921.realchat.data.chat.buildChatCompletionsUrl
import io.github.c1921.realchat.data.settings.AppPreferences
import io.github.c1921.realchat.data.settings.AppPreferencesRepository
import io.github.c1921.realchat.data.settings.DataStoreAppPreferencesRepository
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import io.github.c1921.realchat.model.Conversation
import io.github.c1921.realchat.model.ConversationListItem
import io.github.c1921.realchat.model.ConversationWithMessages
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.UserPersona
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class AppScreen {
    Conversations,
    Characters,
    Settings
}

sealed interface SecondaryScreen {
    data class ChatDetail(val conversationId: Long) : SecondaryScreen
}

enum class CharacterEditorField {
    Name,
    Description,
    Personality,
    Scenario,
    FirstMessage,
    ExampleMessages,
    SystemPrompt,
    PostHistoryInstructions,
    CreatorNotes,
    Tags,
    Creator,
    CharacterVersion,
    AlternateGreetings
}

data class ConversationUiState(
    val conversationItems: List<ConversationListItem> = emptyList(),
    val selectedConversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val errorText: String? = null,
    val statusText: String? = null,
    val hasValidConfig: Boolean = false,
    val showCreateDialog: Boolean = false,
    val pendingConversationTitle: String = "",
    val pendingCharacterCardId: Long? = null,
    val showRenameDialog: Boolean = false,
    val pendingRenameTitle: String = ""
) {
    fun selectedConversation(): Conversation? {
        return conversationItems.firstOrNull { it.conversation.id == selectedConversationId }
            ?.conversation
    }
}

data class CharacterCardEditorState(
    val editingCardId: Long? = null,
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val tagsText: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val alternateGreetingsText: String = "",
    val rawExtensionsJson: String = "{}",
    val rawUnknownJson: String = "{}"
)

data class CharacterCardsUiState(
    val cards: List<CharacterCard> = emptyList(),
    val isEditing: Boolean = false,
    val editor: CharacterCardEditorState = CharacterCardEditorState(),
    val errorText: String? = null,
    val statusText: String? = null,
    val pendingExport: CharacterCardExportPayload? = null
)

data class SettingsUiState(
    val apiKey: String = "",
    val model: String = ProviderConfig.DEFAULT_MODEL,
    val baseUrl: String = ProviderConfig.DEFAULT_BASE_URL,
    val personaName: String = "",
    val personaDescription: String = "",
    val errorText: String? = null,
    val statusText: String? = null
)

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.Conversations,
    val secondaryScreen: SecondaryScreen? = null,
    val conversation: ConversationUiState = ConversationUiState(),
    val characters: CharacterCardsUiState = CharacterCardsUiState(),
    val settings: SettingsUiState = SettingsUiState()
)

class ChatViewModel(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val characterCardRepository: CharacterCardRepository,
    private val conversationRepository: ConversationRepository,
    private val chatProvider: ChatProvider,
    private val promptComposer: PromptComposer
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val draftWriteMutex = Mutex()

    private var activePreferences: AppPreferences = AppPreferences()
    private var availableCards: List<CharacterCard> = emptyList()
    private var activeConversationBundle: ConversationWithMessages? = null
    private var activeConversationJob: Job? = null
    private var lastAppliedProviderConfig: ProviderConfig? = null
    private var lastAppliedUserPersona: UserPersona? = null

    init {
        bootstrap()
        observePreferences()
        observeCharacterCards()
        observeConversations()
    }

    fun openScreen(screen: AppScreen) {
        _uiState.update { current ->
            current.copy(
                currentScreen = screen,
                secondaryScreen = null
            )
        }
    }

    fun openConversationDetail(conversationId: Long) {
        selectConversationInternal(conversationId, persistSelection = true)
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Conversations,
                secondaryScreen = SecondaryScreen.ChatDetail(conversationId)
            )
        }
    }

    fun closeSecondaryScreen() {
        _uiState.update { current ->
            current.copy(secondaryScreen = null)
        }
    }

    fun updateDraft(draft: String) {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    draft = draft,
                    errorText = null,
                    statusText = null
                )
            )
        }

        val conversationId = uiState.value.conversation.selectedConversationId ?: return
        viewModelScope.launch {
            runCatching {
                draftWriteMutex.withLock {
                    conversationRepository.updateDraft(conversationId, draft)
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            errorText = throwable.message ?: "保存草稿失败。"
                        )
                    )
                }
            }
        }
    }

    fun showCreateConversationDialog() {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    showCreateDialog = true,
                    pendingConversationTitle = "",
                    pendingCharacterCardId = current.conversation.pendingCharacterCardId
                        ?: availableCards.firstOrNull()?.id
                )
            )
        }
    }

    fun dismissCreateConversationDialog() {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    showCreateDialog = false,
                    pendingConversationTitle = ""
                )
            )
        }
    }

    fun updatePendingConversationTitle(title: String) {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    pendingConversationTitle = title
                )
            )
        }
    }

    fun updatePendingConversationCardId(cardId: Long) {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    pendingCharacterCardId = cardId
                )
            )
        }
    }

    fun createConversation() {
        val state = uiState.value
        val selectedCard = availableCards.firstOrNull {
            it.id == state.conversation.pendingCharacterCardId
        } ?: availableCards.firstOrNull()

        if (selectedCard == null) {
            _uiState.update { current ->
                current.copy(
                    conversation = current.conversation.copy(
                        errorText = "请先创建至少一张角色卡。"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                conversationRepository.createConversation(
                    characterCard = selectedCard,
                    title = state.conversation.pendingConversationTitle
                )
            }.onSuccess { conversationId ->
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            showCreateDialog = false,
                            pendingConversationTitle = "",
                            errorText = null,
                            statusText = "已创建新会话。"
                        )
                    )
                }
                selectConversationInternal(conversationId, persistSelection = true)
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            errorText = throwable.message ?: "创建会话失败。"
                        )
                    )
                }
            }
        }
    }

    fun selectConversation(conversationId: Long) {
        selectConversationInternal(conversationId, persistSelection = true)
    }

    fun showRenameConversationDialog() {
        val selectedConversation = uiState.value.conversation.selectedConversation() ?: return
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    showRenameDialog = true,
                    pendingRenameTitle = selectedConversation.effectiveTitle(),
                    errorText = null
                )
            )
        }
    }

    fun dismissRenameConversationDialog() {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    showRenameDialog = false,
                    pendingRenameTitle = ""
                )
            )
        }
    }

    fun updatePendingRenameTitle(title: String) {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    pendingRenameTitle = title
                )
            )
        }
    }

    fun renameSelectedConversation() {
        val conversationId = uiState.value.conversation.selectedConversationId ?: return
        val title = uiState.value.conversation.pendingRenameTitle
        viewModelScope.launch {
            runCatching {
                conversationRepository.renameConversation(conversationId, title)
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            showRenameDialog = false,
                            pendingRenameTitle = "",
                            errorText = null,
                            statusText = "会话标题已更新。"
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            errorText = throwable.message ?: "重命名会话失败。"
                        )
                    )
                }
            }
        }
    }

    fun deleteSelectedConversation() {
        val conversationId = uiState.value.conversation.selectedConversationId ?: return
        val shouldRecreate = uiState.value.conversation.conversationItems.size <= 1
        viewModelScope.launch {
            runCatching {
                conversationRepository.deleteConversation(conversationId)
                if (shouldRecreate) {
                    val seedCard = characterCardRepository.ensureSeedCard()
                    val recreatedId = conversationRepository.ensureConversationExists(seedCard)
                    selectConversationInternal(recreatedId, persistSelection = true)
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            errorText = throwable.message ?: "删除会话失败。"
                        )
                    )
                }
            }
        }
    }

    fun updateApiKey(apiKey: String) {
        _uiState.update { current ->
            current.copy(
                settings = current.settings.copy(
                    apiKey = apiKey,
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    fun updateModel(model: String) {
        _uiState.update { current ->
            current.copy(
                settings = current.settings.copy(
                    model = model,
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    fun updateBaseUrl(baseUrl: String) {
        _uiState.update { current ->
            current.copy(
                settings = current.settings.copy(
                    baseUrl = baseUrl,
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    fun updatePersonaName(name: String) {
        _uiState.update { current ->
            current.copy(
                settings = current.settings.copy(
                    personaName = name,
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    fun updatePersonaDescription(description: String) {
        _uiState.update { current ->
            current.copy(
                settings = current.settings.copy(
                    personaDescription = description,
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    fun saveSettings() {
        val form = uiState.value.settings
        val config = ProviderConfig(
            apiKey = form.apiKey,
            model = form.model,
            baseUrl = form.baseUrl
        ).normalized()
        val persona = UserPersona(
            displayName = form.personaName,
            description = form.personaDescription
        ).normalized()

        if (config.baseUrl.isNotEmpty()) {
            val endpoint = buildChatCompletionsUrl(config.baseUrl)
            if (endpoint.toHttpUrlOrNull() == null) {
                _uiState.update { current ->
                    current.copy(
                        settings = current.settings.copy(
                            errorText = "Base URL 格式不正确。",
                            statusText = null
                        )
                    )
                }
                return
            }
        }

        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.saveProviderConfig(config)
                appPreferencesRepository.saveUserPersona(persona)
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        settings = current.settings.copy(
                            apiKey = config.apiKey,
                            model = config.model,
                            baseUrl = config.baseUrl,
                            personaName = persona.displayName,
                            personaDescription = persona.description,
                            errorText = null,
                            statusText = "设置已保存。"
                        ),
                        conversation = current.conversation.copy(
                            hasValidConfig = config.hasRequiredFields(),
                            errorText = null
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        settings = current.settings.copy(
                            errorText = throwable.message ?: "保存设置失败。",
                            statusText = null
                        )
                    )
                }
            }
        }
    }

    fun openCreateCharacterEditor() {
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Characters,
                secondaryScreen = null,
                characters = current.characters.copy(
                    isEditing = true,
                    editor = CharacterCardEditorState(),
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    fun openEditCharacterEditor(cardId: Long) {
        val card = availableCards.firstOrNull { it.id == cardId } ?: return
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Characters,
                secondaryScreen = null,
                characters = current.characters.copy(
                    isEditing = true,
                    editor = card.toEditorState(),
                    errorText = null
                )
            )
        }
    }

    fun cancelCharacterEditing() {
        _uiState.update { current ->
            current.copy(
                characters = current.characters.copy(
                    isEditing = false,
                    editor = CharacterCardEditorState(),
                    errorText = null
                )
            )
        }
    }

    fun updateCharacterEditor(field: CharacterEditorField, value: String) {
        _uiState.update { current ->
            current.copy(
                characters = current.characters.copy(
                    editor = current.characters.editor.updated(field, value),
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    fun saveCharacterCard() {
        val editor = uiState.value.characters.editor
        if (editor.name.trim().isEmpty()) {
            _uiState.update { current ->
                current.copy(
                    characters = current.characters.copy(
                        errorText = "角色名不能为空。"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                characterCardRepository.saveCard(editor.toCharacterCard())
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        characters = current.characters.copy(
                            isEditing = false,
                            editor = CharacterCardEditorState(),
                            errorText = null,
                            statusText = "角色卡已保存。"
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        characters = current.characters.copy(
                            errorText = throwable.message ?: "保存角色卡失败。"
                        )
                    )
                }
            }
        }
    }

    fun duplicateCharacterCard(cardId: Long) {
        viewModelScope.launch {
            runCatching {
                characterCardRepository.duplicateCard(cardId)
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        characters = current.characters.copy(
                            errorText = null,
                            statusText = "角色卡已复制。"
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        characters = current.characters.copy(
                            errorText = throwable.message ?: "复制角色卡失败。"
                        )
                    )
                }
            }
        }
    }

    fun deleteCharacterCard(cardId: Long) {
        val shouldReseed = uiState.value.characters.cards.size <= 1
        viewModelScope.launch {
            runCatching {
                characterCardRepository.deleteCard(cardId)
                if (shouldReseed) {
                    characterCardRepository.ensureSeedCard()
                }
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        characters = current.characters.copy(
                            errorText = null,
                            statusText = "角色卡已删除。"
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        characters = current.characters.copy(
                            errorText = throwable.message ?: "删除角色卡失败。"
                        )
                    )
                }
            }
        }
    }

    fun importCharacterCard(jsonText: String) {
        viewModelScope.launch {
            runCatching {
                characterCardRepository.importCard(jsonText)
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        currentScreen = AppScreen.Characters,
                        secondaryScreen = null,
                        characters = current.characters.copy(
                            errorText = null,
                            statusText = "角色卡导入成功。"
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        currentScreen = AppScreen.Characters,
                        secondaryScreen = null,
                        characters = current.characters.copy(
                            errorText = throwable.message ?: "角色卡导入失败。"
                        )
                    )
                }
            }
        }
    }

    fun reportCharacterError(errorText: String) {
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Characters,
                secondaryScreen = null,
                characters = current.characters.copy(
                    errorText = errorText
                )
            )
        }
    }

    fun requestCharacterCardExport(cardId: Long) {
        viewModelScope.launch {
            runCatching {
                characterCardRepository.exportCard(cardId)
            }.onSuccess { payload ->
                if (payload == null) {
                    _uiState.update { current ->
                        current.copy(
                            characters = current.characters.copy(
                                errorText = "角色卡不存在。"
                            )
                        )
                    }
                    return@onSuccess
                }
                _uiState.update { current ->
                    current.copy(
                        currentScreen = AppScreen.Characters,
                        secondaryScreen = null,
                        characters = current.characters.copy(
                            pendingExport = payload,
                            errorText = null
                        )
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        characters = current.characters.copy(
                            errorText = throwable.message ?: "导出角色卡失败。"
                        )
                    )
                }
            }
        }
    }

    fun clearPendingCharacterCardExport() {
        _uiState.update { current ->
            current.copy(
                characters = current.characters.copy(
                    pendingExport = null
                )
            )
        }
    }

    fun onCharacterCardExportCompleted(errorText: String?) {
        _uiState.update { current ->
            current.copy(
                characters = current.characters.copy(
                    pendingExport = null,
                    errorText = errorText,
                    statusText = if (errorText == null) "角色卡已导出。" else null
                )
            )
        }
    }

    fun sendMessage() {
        val currentState = uiState.value
        if (currentState.conversation.isSending) {
            return
        }

        val normalizedConfig = activePreferences.providerConfig.normalized()
        if (!normalizedConfig.hasRequiredFields()) {
            _uiState.update { current ->
                current.copy(
                    conversation = current.conversation.copy(
                        errorText = "请先在设置中保存 API Key、模型和 Base URL。"
                    )
                )
            }
            return
        }

        val activeBundle = activeConversationBundle
        if (activeBundle == null) {
            _uiState.update { current ->
                current.copy(
                    conversation = current.conversation.copy(
                        errorText = "请先选择一个会话。"
                    )
                )
            }
            return
        }

        val userContent = currentState.conversation.draft.trim()
        if (userContent.isEmpty()) {
            return
        }

        val historyMessages = activeBundle.messages + ChatMessage(
            role = ChatRole.User,
            content = userContent
        )

        val requestMessages = promptComposer.compose(
            characterSnapshot = activeBundle.conversation.characterSnapshot,
            userPersona = activePreferences.userPersona,
            conversationMessages = historyMessages
        )

        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    isSending = true,
                    errorText = null,
                    statusText = null
                )
            )
        }

        viewModelScope.launch {
            chatProvider.send(
                messages = requestMessages,
                config = normalizedConfig
            ).fold(
                onSuccess = { assistantMessage ->
                    runCatching {
                        conversationRepository.appendSuccessfulExchange(
                            conversationId = activeBundle.conversation.id,
                            userContent = userContent,
                            assistantMessage = assistantMessage
                        )
                    }.onSuccess {
                        _uiState.update { current ->
                            current.copy(
                                conversation = current.conversation.copy(
                                    isSending = false,
                                    errorText = null
                                )
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update { current ->
                            current.copy(
                                conversation = current.conversation.copy(
                                    isSending = false,
                                    errorText = throwable.message ?: "保存聊天记录失败。"
                                )
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            conversation = current.conversation.copy(
                                isSending = false,
                                errorText = throwable.message ?: "请求失败。"
                            )
                        )
                    }
                }
            )
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val seedCard = characterCardRepository.ensureSeedCard()
            conversationRepository.ensureConversationExists(seedCard)
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            appPreferencesRepository.observePreferences().collect { preferences ->
                activePreferences = preferences
                val normalizedConfig = preferences.providerConfig.normalized()
                val providerChanged = lastAppliedProviderConfig != preferences.providerConfig
                val personaChanged = lastAppliedUserPersona != preferences.userPersona
                lastAppliedProviderConfig = preferences.providerConfig
                lastAppliedUserPersona = preferences.userPersona
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            hasValidConfig = normalizedConfig.hasRequiredFields()
                        ),
                        settings = if (providerChanged || personaChanged) {
                            current.settings.copy(
                                apiKey = preferences.providerConfig.apiKey,
                                model = preferences.providerConfig.model,
                                baseUrl = preferences.providerConfig.baseUrl,
                                personaName = preferences.userPersona.displayName,
                                personaDescription = preferences.userPersona.description
                            )
                        } else {
                            current.settings
                        }
                    )
                }
            }
        }
    }

    private fun observeCharacterCards() {
        viewModelScope.launch {
            characterCardRepository.observeCards().collect { cards ->
                availableCards = cards
                _uiState.update { current ->
                    val safePendingCardId = current.conversation.pendingCharacterCardId
                        ?.takeIf { pendingId -> cards.any { it.id == pendingId } }
                        ?: cards.firstOrNull()?.id

                    current.copy(
                        characters = current.characters.copy(cards = cards),
                        conversation = current.conversation.copy(
                            pendingCharacterCardId = safePendingCardId
                        )
                    )
                }
            }
        }
    }

    private fun observeConversations() {
        viewModelScope.launch {
            conversationRepository.observeConversationListItems().collect { conversationItems ->
                val currentSelectedId = uiState.value.conversation.selectedConversationId
                val preferredId = when {
                    currentSelectedId != null &&
                        conversationItems.any { it.conversation.id == currentSelectedId } ->
                        currentSelectedId

                    activePreferences.selectedConversationId != null &&
                        conversationItems.any {
                            it.conversation.id == activePreferences.selectedConversationId
                        } ->
                        activePreferences.selectedConversationId

                    else -> conversationItems.firstOrNull()?.conversation?.id
                }

                _uiState.update { current ->
                    val secondaryScreen = when (val activeSecondary = current.secondaryScreen) {
                        is SecondaryScreen.ChatDetail -> {
                            when {
                                conversationItems.any {
                                    it.conversation.id == activeSecondary.conversationId
                                } -> activeSecondary

                                preferredId != null -> SecondaryScreen.ChatDetail(preferredId)

                                else -> null
                            }
                        }

                        null -> null
                    }

                    current.copy(
                        secondaryScreen = secondaryScreen,
                        conversation = current.conversation.copy(
                            conversationItems = conversationItems,
                            selectedConversationId = preferredId
                        )
                    )
                }

                if (preferredId != currentSelectedId || activeConversationJob == null) {
                    selectConversationInternal(
                        conversationId = preferredId,
                        persistSelection = preferredId != activePreferences.selectedConversationId
                    )
                }
            }
        }
    }

    private fun selectConversationInternal(
        conversationId: Long?,
        persistSelection: Boolean
    ) {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    selectedConversationId = conversationId
                )
            )
        }

        observeSelectedConversation(conversationId)

        if (persistSelection) {
            viewModelScope.launch {
                appPreferencesRepository.saveSelectedConversationId(conversationId)
            }
        }
    }

    private fun observeSelectedConversation(conversationId: Long?) {
        activeConversationJob?.cancel()
        activeConversationBundle = null

        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    messages = emptyList(),
                    draft = "",
                    isSending = false
                )
            )
        }

        if (conversationId == null) {
            return
        }

        activeConversationJob = viewModelScope.launch {
            conversationRepository.observeConversationWithMessages(conversationId).collect { bundle ->
                activeConversationBundle = bundle
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            messages = bundle?.messages.orEmpty(),
                            draft = bundle?.conversation?.draft.orEmpty()
                        )
                    )
                }
            }
        }
    }

    private fun CharacterCard.toEditorState(): CharacterCardEditorState {
        return CharacterCardEditorState(
            editingCardId = id,
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            firstMes = firstMes,
            mesExample = mesExample,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            creatorNotes = creatorNotes,
            tagsText = tags.joinToString(", "),
            creator = creator,
            characterVersion = characterVersion,
            alternateGreetingsText = alternateGreetings.joinToString(separator = "\n"),
            rawExtensionsJson = rawExtensionsJson,
            rawUnknownJson = rawUnknownJson
        )
    }

    private fun CharacterCardEditorState.updated(
        field: CharacterEditorField,
        value: String
    ): CharacterCardEditorState {
        return when (field) {
            CharacterEditorField.Name -> copy(name = value)
            CharacterEditorField.Description -> copy(description = value)
            CharacterEditorField.Personality -> copy(personality = value)
            CharacterEditorField.Scenario -> copy(scenario = value)
            CharacterEditorField.FirstMessage -> copy(firstMes = value)
            CharacterEditorField.ExampleMessages -> copy(mesExample = value)
            CharacterEditorField.SystemPrompt -> copy(systemPrompt = value)
            CharacterEditorField.PostHistoryInstructions -> copy(postHistoryInstructions = value)
            CharacterEditorField.CreatorNotes -> copy(creatorNotes = value)
            CharacterEditorField.Tags -> copy(tagsText = value)
            CharacterEditorField.Creator -> copy(creator = value)
            CharacterEditorField.CharacterVersion -> copy(characterVersion = value)
            CharacterEditorField.AlternateGreetings -> copy(alternateGreetingsText = value)
        }
    }

    private fun CharacterCardEditorState.toCharacterCard(): CharacterCard {
        return CharacterCard(
            id = editingCardId ?: 0L,
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            firstMes = firstMes,
            mesExample = mesExample,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            alternateGreetings = alternateGreetingsText.lines()
                .map(String::trim)
                .filter(String::isNotEmpty),
            creatorNotes = creatorNotes,
            tags = tagsText.split(',')
                .map(String::trim)
                .filter(String::isNotEmpty),
            creator = creator,
            characterVersion = characterVersion,
            rawExtensionsJson = rawExtensionsJson,
            rawUnknownJson = rawUnknownJson
        )
    }

    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = AppDatabase.getInstance(context)
            val preferencesRepository = DataStoreAppPreferencesRepository(context)
            val characterCardRepository = RoomCharacterCardRepository(database.characterCardDao())
            val conversationRepository = RoomConversationRepository(database)
            val provider = DeepSeekChatProvider()
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                appPreferencesRepository = preferencesRepository,
                characterCardRepository = characterCardRepository,
                conversationRepository = conversationRepository,
                chatProvider = provider,
                promptComposer = PromptComposer()
            ) as T
        }
    }
}
