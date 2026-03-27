package io.github.c1921.realchat.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.realchat.data.agent.DirectorService
import io.github.c1921.realchat.data.agent.EmotionUpdater
import io.github.c1921.realchat.data.agent.MemorySummarizer
import io.github.c1921.realchat.data.agent.OpenAiCompatibleDirectorService
import io.github.c1921.realchat.data.agent.OpenAiCompatibleEmotionUpdater
import io.github.c1921.realchat.data.agent.OpenAiCompatibleMemorySummarizer
import io.github.c1921.realchat.data.agent.ProactiveMessagingController
import io.github.c1921.realchat.data.character.CharacterCardExportPayload
import io.github.c1921.realchat.data.character.CharacterCardRepository
import io.github.c1921.realchat.data.character.RoomCharacterCardRepository
import io.github.c1921.realchat.data.chat.AppDatabase
import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.data.chat.ConversationRepository
import io.github.c1921.realchat.data.chat.OpenAiCompatibleChatProvider
import io.github.c1921.realchat.data.chat.PromptComposer
import io.github.c1921.realchat.data.chat.RoomConversationRepository
import io.github.c1921.realchat.data.chat.buildChatCompletionsUrl
import io.github.c1921.realchat.data.settings.AppPreferences
import io.github.c1921.realchat.data.settings.AppPreferencesRepository
import io.github.c1921.realchat.data.settings.DataStoreAppPreferencesRepository
import io.github.c1921.realchat.model.AgentSettings
import io.github.c1921.realchat.model.DirectorSettings
import io.github.c1921.realchat.model.MemorySettings
import io.github.c1921.realchat.model.ProactiveSettings
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.Conversation
import io.github.c1921.realchat.model.ConversationListItem
import io.github.c1921.realchat.model.ConversationWithMessages
import io.github.c1921.realchat.model.DirectorGuidance
import io.github.c1921.realchat.model.EmotionState
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.ProviderType
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
    val isDirectorAnalyzing: Boolean = false,
    val errorText: String? = null,
    val statusText: String? = null,
    val hasValidConfig: Boolean = false,
    val showCreateDialog: Boolean = false,
    val pendingCharacterCardId: Long? = null,
    val emotionState: EmotionState = EmotionState(),
    val directorGuidanceHints: Map<Int, String> = emptyMap(),
    val optimisticUserMessage: ChatMessage? = null
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
    val providerType: ProviderType = ProviderConfig.DEFAULT_PROVIDER_TYPE,
    val apiKey: String = "",
    val model: String = ProviderConfig.DEFAULT_MODEL,
    val baseUrl: String = ProviderConfig.DEFAULT_BASE_URL,
    val personaName: String = "",
    val personaDescription: String = "",
    val proactiveEnabled: Boolean = false,
    val proactiveMinIntervalMinutes: Int = 30,
    val proactiveMaxIntervalMinutes: Int = 1440,
    val proactiveMaxCount: Int = 5,
    val directorEnabled: Boolean = false,
    val directorSystemPrompt: String = "",
    val memoryEnabled: Boolean = false,
    val memoryTriggerCount: Int = 40,
    val memoryKeepCount: Int = 10,
    val developerModeEnabled: Boolean = false,
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
    private val promptComposer: PromptComposer,
    private val directorService: DirectorService,
    private val emotionUpdater: EmotionUpdater,
    private val memorySummarizer: MemorySummarizer
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val draftWriteMutex = Mutex()

    private var activePreferences: AppPreferences = AppPreferences()
    private var activeAgentSettings: AgentSettings = AgentSettings()
    private var activeEmotionState: EmotionState = EmotionState()
    private var providerDrafts: MutableMap<ProviderType, ProviderConfig> =
        ProviderConfig.defaultsByProvider().toMutableMap()
    private var availableCards: List<CharacterCard> = emptyList()
    private var activeConversationBundle: ConversationWithMessages? = null
    private var activeConversationJob: Job? = null
    private var lastAppliedSelectedProviderType: ProviderType? = null
    private var lastAppliedProviderConfigs: Map<ProviderType, ProviderConfig>? = null
    private var lastAppliedUserPersona: UserPersona? = null
    private var lastAppliedAgentSettings: AgentSettings? = null

    internal val proactiveController = ProactiveMessagingController(
        scope = viewModelScope,
        onTrigger = { elapsedMs -> sendProactiveMessage(elapsedMs) }
    )

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
                    showCreateDialog = false
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
                conversationRepository.createConversation(characterCard = selectedCard)
            }.onSuccess { conversationId ->
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            showCreateDialog = false,
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
        updateCurrentProviderDraft { currentDraft ->
            currentDraft.copy(apiKey = apiKey)
        }
    }

    fun updateProviderType(providerType: ProviderType) {
        val targetDraft = providerDraft(providerType)
        _uiState.update { current ->
            current.copy(
                settings = current.settings.copy(
                    providerType = providerType,
                    apiKey = targetDraft.apiKey,
                    model = targetDraft.model,
                    baseUrl = targetDraft.baseUrl,
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    fun updateModel(model: String) {
        updateCurrentProviderDraft { currentDraft ->
            currentDraft.copy(model = model)
        }
    }

    fun updateBaseUrl(baseUrl: String) {
        updateCurrentProviderDraft { currentDraft ->
            currentDraft.copy(baseUrl = baseUrl)
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

    fun updateProactiveEnabled(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(proactiveEnabled = enabled))
        }
    }

    fun updateDeveloperModeEnabled(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(developerModeEnabled = enabled))
        }
    }

    fun getProactiveNextTriggerMs(): Long = proactiveController.getNextTriggerMs()

    fun getProactiveSentCount(): Int = proactiveController.getSentCount()

    fun updateProactiveMinInterval(minutes: Int) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(proactiveMinIntervalMinutes = minutes))
        }
    }

    fun updateProactiveMaxInterval(minutes: Int) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(proactiveMaxIntervalMinutes = minutes))
        }
    }

    fun updateProactiveMaxCount(count: Int) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(proactiveMaxCount = count))
        }
    }

    fun updateDirectorEnabled(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(directorEnabled = enabled))
        }
    }

    fun updateDirectorSystemPrompt(prompt: String) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(directorSystemPrompt = prompt))
        }
    }

    fun updateMemoryEnabled(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(memoryEnabled = enabled))
        }
    }

    fun updateMemoryTriggerCount(count: Int) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(memoryTriggerCount = count))
        }
    }

    fun updateMemoryKeepCount(count: Int) {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(memoryKeepCount = count))
        }
    }

    fun saveSettings() {
        val form = uiState.value.settings
        val selectedConfig = ProviderConfig(
            providerType = form.providerType,
            apiKey = form.apiKey,
            model = form.model,
            baseUrl = form.baseUrl
        ).normalized()
        providerDrafts[form.providerType] = selectedConfig
        val providerConfigs = ProviderType.entries.associateWith { providerType ->
            (providerDrafts[providerType] ?: ProviderConfig.defaultsFor(providerType))
                .copy(providerType = providerType)
                .normalized()
        }
        val persona = UserPersona(
            displayName = form.personaName,
            description = form.personaDescription
        ).normalized()

        if (selectedConfig.baseUrl.isNotEmpty()) {
            val endpoint = buildChatCompletionsUrl(selectedConfig.baseUrl)
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

        val agentSettings = AgentSettings(
            proactive = ProactiveSettings(
                enabled = form.proactiveEnabled,
                minIntervalMinutes = form.proactiveMinIntervalMinutes.coerceAtLeast(3),
                maxIntervalMinutes = form.proactiveMaxIntervalMinutes
                    .coerceAtLeast(form.proactiveMinIntervalMinutes.coerceAtLeast(3)),
                maxCount = form.proactiveMaxCount.coerceAtLeast(1)
            ),
            director = DirectorSettings(
                enabled = form.directorEnabled,
                systemPrompt = form.directorSystemPrompt
            ),
            memory = MemorySettings(
                enabled = form.memoryEnabled,
                triggerCount = form.memoryTriggerCount,
                keepRecentCount = form.memoryKeepCount
            )
        )

        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.saveProviderSettings(
                    selectedProviderType = form.providerType,
                    providerConfigs = providerConfigs
                )
                appPreferencesRepository.saveUserPersona(persona)
                appPreferencesRepository.saveAgentSettings(agentSettings)
                appPreferencesRepository.saveDeveloperMode(form.developerModeEnabled)
            }.onSuccess {
                providerDrafts = providerConfigs.toMutableMap()
                _uiState.update { current ->
                    current.copy(
                        settings = current.settings.copy(
                            providerType = selectedConfig.providerType,
                            apiKey = selectedConfig.apiKey,
                            model = selectedConfig.model,
                            baseUrl = selectedConfig.baseUrl,
                            personaName = persona.displayName,
                            personaDescription = persona.description,
                            errorText = null,
                            statusText = "设置已保存。"
                        ),
                        conversation = current.conversation.copy(
                            hasValidConfig = selectedConfig.hasRequiredFields(),
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

    private fun updateCurrentProviderDraft(
        transform: (ProviderConfig) -> ProviderConfig
    ) {
        val providerType = uiState.value.settings.providerType
        val updatedDraft = transform(providerDraft(providerType))
            .copy(providerType = providerType)
        providerDrafts[providerType] = updatedDraft
        _uiState.update { current ->
            current.copy(
                settings = current.settings.copy(
                    apiKey = updatedDraft.apiKey,
                    model = updatedDraft.model,
                    baseUrl = updatedDraft.baseUrl,
                    errorText = null,
                    statusText = null
                )
            )
        }
    }

    private fun providerDraft(providerType: ProviderType): ProviderConfig {
        return providerDrafts[providerType]
            ?: ProviderConfig.defaultsFor(providerType)
    }

    private fun syncSettingsState(
        current: SettingsUiState,
        selectedProviderType: ProviderType,
        userPersona: UserPersona,
        agentSettings: AgentSettings,
        developerModeEnabled: Boolean,
        applyProviderDraft: Boolean
    ): SettingsUiState {
        val settingsWithProvider = if (applyProviderDraft) {
            val draft = providerDraft(selectedProviderType)
            current.copy(
                providerType = selectedProviderType,
                apiKey = draft.apiKey,
                model = draft.model,
                baseUrl = draft.baseUrl
            )
        } else {
            current
        }

        return settingsWithProvider.copy(
            personaName = userPersona.displayName,
            personaDescription = userPersona.description,
            proactiveEnabled = agentSettings.proactive.enabled,
            proactiveMinIntervalMinutes = agentSettings.proactive.minIntervalMinutes,
            proactiveMaxIntervalMinutes = agentSettings.proactive.maxIntervalMinutes,
            proactiveMaxCount = agentSettings.proactive.maxCount,
            directorEnabled = agentSettings.director.enabled,
            directorSystemPrompt = agentSettings.director.systemPrompt,
            memoryEnabled = agentSettings.memory.enabled,
            memoryTriggerCount = agentSettings.memory.triggerCount,
            memoryKeepCount = agentSettings.memory.keepRecentCount,
            developerModeEnabled = developerModeEnabled
        )
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
                        errorText = "请先在设置中保存 AI Provider、API Key、模型和 Base URL。"
                    )
                )
            }
            return
        }

        if (activeConversationBundle == null) {
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

        viewModelScope.launch {
            sendMessageInternal(userContent = userContent, catalyst = null)
        }
    }

    private fun sendProactiveMessage(elapsedMs: Long) {
        if (uiState.value.conversation.isSending) return
        val bundle = activeConversationBundle ?: return
        val normalizedConfig = activePreferences.providerConfig.normalized()
        if (!normalizedConfig.hasRequiredFields()) return

        val characterName = bundle.conversation.characterSnapshot?.effectiveName()
            ?: CharacterCardSnapshot.DEFAULT_CHARACTER_NAME
        val elapsedMinutes = elapsedMs / 60_000
        val catalyst = "距上次对话已过去约 $elapsedMinutes 分钟，请以 $characterName 的身份主动发起新话题或表达关心。"

        viewModelScope.launch {
            sendMessageInternal(userContent = null, catalyst = catalyst)
        }
    }

    private suspend fun sendMessageInternal(userContent: String?, catalyst: String?) {
        val activeBundle = activeConversationBundle ?: return
        val normalizedConfig = activePreferences.providerConfig.normalized()

        val historyMessages = if (userContent != null) {
            activeBundle.messages + ChatMessage(role = ChatRole.User, content = userContent)
        } else {
            activeBundle.messages
        }

        if (userContent != null) {
            _uiState.update { current ->
                current.copy(
                    conversation = current.conversation.copy(
                        optimisticUserMessage = ChatMessage(role = ChatRole.User, content = userContent),
                        draft = "",
                        isSending = true,
                        errorText = null,
                        statusText = null
                    )
                )
            }
        } else {
            _uiState.update { current ->
                current.copy(
                    conversation = current.conversation.copy(
                        isSending = true,
                        errorText = null,
                        statusText = null
                    )
                )
            }
        }

        checkAndSummarizeIfNeeded(
            messages = historyMessages,
            conversationId = activeBundle.conversation.id,
            config = normalizedConfig,
            snapshot = activeBundle.conversation.characterSnapshot
        )

        val guidance = if (activeAgentSettings.director.enabled) {
            _uiState.update { current ->
                current.copy(
                    conversation = current.conversation.copy(
                        isDirectorAnalyzing = true,
                        statusText = "导演分析中..."
                    )
                )
            }
            val result = directorService.analyze(
                snapshot = activeBundle.conversation.characterSnapshot,
                emotionState = activeEmotionState,
                conversationMessages = historyMessages,
                config = normalizedConfig
            ).getOrNull()
            _uiState.update { current ->
                current.copy(
                    conversation = current.conversation.copy(
                        isDirectorAnalyzing = false,
                        statusText = null
                    )
                )
            }
            result
        } else {
            null
        }

        val requestMessages = promptComposer.compose(
            characterSnapshot = activeBundle.conversation.characterSnapshot,
            userPersona = activePreferences.userPersona,
            conversationMessages = historyMessages,
            directorGuidance = guidance,
            proactiveCatalyst = catalyst,
            emotionState = activeEmotionState
        )

        chatProvider.send(
            messages = requestMessages,
            config = normalizedConfig
        ).fold(
            onSuccess = { assistantMessage ->
                runCatching {
                    if (userContent != null) {
                        conversationRepository.appendSuccessfulExchange(
                            conversationId = activeBundle.conversation.id,
                            userContent = userContent,
                            assistantMessage = assistantMessage
                        )
                    } else {
                        conversationRepository.appendProactiveMessage(
                            conversationId = activeBundle.conversation.id,
                            assistantMessage = assistantMessage
                        )
                    }
                }.onSuccess {
                    proactiveController.updateLastMessageTimestamp(System.currentTimeMillis())
                    if (userContent != null) {
                        proactiveController.resetCount()
                    }
                    val guidanceText = guidance?.let { formatDirectorGuidance(it) }
                    val assistantIndex = historyMessages.size
                    _uiState.update { current ->
                        val updatedHints = if (guidanceText != null) {
                            current.conversation.directorGuidanceHints + (assistantIndex to guidanceText)
                        } else {
                            current.conversation.directorGuidanceHints
                        }
                        current.copy(
                            conversation = current.conversation.copy(
                                isSending = false,
                                optimisticUserMessage = null,
                                errorText = null,
                                directorGuidanceHints = updatedHints
                            )
                        )
                    }
                    updateEmotionAsync(
                        conversationId = activeBundle.conversation.id,
                        snapshot = activeBundle.conversation.characterSnapshot,
                        recentMessages = historyMessages + assistantMessage,
                        config = normalizedConfig
                    )
                }.onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            conversation = current.conversation.copy(
                                isSending = false,
                                optimisticUserMessage = null,
                                draft = userContent ?: current.conversation.draft,
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
                            isDirectorAnalyzing = false,
                            statusText = null,
                            optimisticUserMessage = null,
                            draft = userContent ?: current.conversation.draft,
                            errorText = throwable.message ?: "请求失败。"
                        )
                    )
                }
            }
        )
    }

    private suspend fun checkAndSummarizeIfNeeded(
        messages: List<ChatMessage>,
        conversationId: Long,
        config: ProviderConfig,
        snapshot: CharacterCardSnapshot?
    ) {
        val memorySettings = activeAgentSettings.memory
        if (!memorySettings.enabled) return
        if (messages.size <= memorySettings.triggerCount) return

        val keepMessages = messages.takeLast(memorySettings.keepRecentCount)
        val toSummarize = messages.dropLast(memorySettings.keepRecentCount)
        if (toSummarize.isEmpty()) return

        memorySummarizer.summarize(
            messagesToSummarize = toSummarize,
            snapshot = snapshot,
            config = config
        ).onSuccess { summary ->
            conversationRepository.replaceWithSummary(
                conversationId = conversationId,
                summaryText = summary,
                keepRecentMessages = keepMessages
            )
        }
    }

    private fun updateEmotionAsync(
        conversationId: Long,
        snapshot: CharacterCardSnapshot?,
        recentMessages: List<ChatMessage>,
        config: ProviderConfig
    ) {
        viewModelScope.launch {
            emotionUpdater.update(
                currentState = activeEmotionState,
                snapshot = snapshot,
                recentMessages = recentMessages,
                config = config
            ).onSuccess { newState ->
                activeEmotionState = newState
                conversationRepository.updateEmotionState(conversationId, newState)
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            emotionState = newState
                        )
                    )
                }
            }
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
                val providerChanged = lastAppliedSelectedProviderType != preferences.selectedProviderType ||
                    lastAppliedProviderConfigs != preferences.providerConfigs
                val personaChanged = lastAppliedUserPersona != preferences.userPersona
                val agentSettingsChanged = lastAppliedAgentSettings != preferences.agentSettings
                lastAppliedSelectedProviderType = preferences.selectedProviderType
                lastAppliedProviderConfigs = preferences.providerConfigs
                lastAppliedUserPersona = preferences.userPersona
                lastAppliedAgentSettings = preferences.agentSettings
                if (providerChanged) {
                    providerDrafts = preferences.providerConfigs.mapValuesTo(mutableMapOf()) { (providerType, config) ->
                        config.copy(providerType = providerType)
                    }
                }
                if (agentSettingsChanged) {
                    activeAgentSettings = preferences.agentSettings
                    (directorService as? OpenAiCompatibleDirectorService)
                        ?.updateSystemPrompt(preferences.agentSettings.director.systemPrompt)
                    if (preferences.agentSettings.proactive.enabled) {
                        val lastTs = activeConversationBundle?.conversation?.updatedAt
                            ?: System.currentTimeMillis()
                        proactiveController.start(preferences.agentSettings.proactive, lastTs)
                    } else {
                        proactiveController.stop()
                    }
                }
                _uiState.update { current ->
                    val syncedSettings = if (providerChanged || personaChanged || agentSettingsChanged) {
                        syncSettingsState(
                            current = current.settings,
                            selectedProviderType = preferences.selectedProviderType,
                            userPersona = preferences.userPersona,
                            agentSettings = preferences.agentSettings,
                            developerModeEnabled = preferences.developerModeEnabled,
                            applyProviderDraft = providerChanged
                        )
                    } else {
                        current.settings
                    }

                    current.copy(
                        conversation = current.conversation.copy(
                            hasValidConfig = normalizedConfig.hasRequiredFields()
                        ),
                        settings = syncedSettings
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
        proactiveController.stop()

        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    messages = emptyList(),
                    draft = "",
                    isSending = false,
                    emotionState = EmotionState(),
                    directorGuidanceHints = emptyMap(),
                    optimisticUserMessage = null
                )
            )
        }

        if (conversationId == null) {
            return
        }

        activeConversationJob = viewModelScope.launch {
            conversationRepository.observeConversationWithMessages(conversationId).collect { bundle ->
                activeConversationBundle = bundle
                if (bundle != null) {
                    activeEmotionState = bundle.conversation.emotionState
                    if (activeAgentSettings.proactive.enabled) {
                        proactiveController.start(
                            settings = activeAgentSettings.proactive,
                            lastMessageTimestampMs = bundle.conversation.updatedAt
                        )
                    }
                }
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            messages = bundle?.messages.orEmpty(),
                            draft = bundle?.conversation?.draft.orEmpty(),
                            emotionState = bundle?.conversation?.emotionState ?: EmotionState()
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

    private fun formatDirectorGuidance(guidance: DirectorGuidance): String? {
        val parts = buildList {
            if (guidance.mood.isNotBlank()) add("氛围：${guidance.mood}")
            if (guidance.topicDirection.isNotBlank()) add("话题方向：${guidance.topicDirection}")
            if (guidance.pursue.isNotBlank()) add("推进：${guidance.pursue}")
            if (guidance.avoid.isNotBlank()) add("避免：${guidance.avoid}")
        }
        return if (parts.isEmpty()) null else "导演指示：${parts.joinToString("，")}"
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
            val provider = OpenAiCompatibleChatProvider()
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                appPreferencesRepository = preferencesRepository,
                characterCardRepository = characterCardRepository,
                conversationRepository = conversationRepository,
                chatProvider = provider,
                promptComposer = PromptComposer(),
                directorService = OpenAiCompatibleDirectorService(chatProvider = provider),
                emotionUpdater = OpenAiCompatibleEmotionUpdater(chatProvider = provider),
                memorySummarizer = OpenAiCompatibleMemorySummarizer(chatProvider = provider)
            ) as T
        }
    }
}
