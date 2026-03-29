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
import io.github.c1921.realchat.data.agent.ProactiveTriggerResult
import io.github.c1921.realchat.data.character.CharacterCardExportPayload
import io.github.c1921.realchat.data.character.CharacterCardRepository
import io.github.c1921.realchat.data.character.RoomCharacterCardRepository
import io.github.c1921.realchat.data.chat.AppDatabase
import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.data.chat.ChatRequestException
import io.github.c1921.realchat.data.chat.ChatRequestTrace
import io.github.c1921.realchat.data.chat.ConversationRepository
import io.github.c1921.realchat.data.chat.OpenAiCompatibleChatProvider
import io.github.c1921.realchat.data.chat.PromptComposer
import io.github.c1921.realchat.data.chat.RoomConversationRepository
import io.github.c1921.realchat.data.chat.buildChatCompletionsUrl
import io.github.c1921.realchat.data.settings.AppPreferences
import io.github.c1921.realchat.data.settings.AppPreferencesRepository
import io.github.c1921.realchat.data.settings.DataStoreAppPreferencesRepository
import io.github.c1921.realchat.model.AgentSettings
import io.github.c1921.realchat.model.AgentExecutionException
import io.github.c1921.realchat.model.DirectorSettings
import io.github.c1921.realchat.model.MemorySettings
import io.github.c1921.realchat.model.ProactiveSettings
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import io.github.c1921.realchat.model.CharacterCardSnapshot
import io.github.c1921.realchat.model.Conversation
import io.github.c1921.realchat.model.ConversationDebugEvent
import io.github.c1921.realchat.model.ConversationDebugSource
import io.github.c1921.realchat.model.ConversationDebugType
import io.github.c1921.realchat.model.ConversationListItem
import io.github.c1921.realchat.model.ConversationWithMessages
import io.github.c1921.realchat.model.DirectorGuidance
import io.github.c1921.realchat.model.EmotionState
import io.github.c1921.realchat.model.ProactiveAction
import io.github.c1921.realchat.model.ProactiveDirectorDecision
import io.github.c1921.realchat.model.ProactiveInstruction
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

enum class SettingsSection {
    Provider,
    Persona,
    Proactive,
    Director,
    Memory
}

sealed interface SecondaryScreen {
    data class ChatDetail(val conversationId: Long) : SecondaryScreen
    data class SettingsDetail(val section: SettingsSection) : SecondaryScreen
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
    val debugEvents: List<ConversationDebugEvent> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val isDirectorAnalyzing: Boolean = false,
    val errorText: String? = null,
    val statusText: String? = null,
    val hasValidConfig: Boolean = false,
    val showCreateDialog: Boolean = false,
    val pendingCharacterCardId: Long? = null,
    val emotionState: EmotionState = EmotionState(),
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
    val providerConfigs: Map<ProviderType, ProviderConfig> = ProviderConfig.defaultsByProvider(),
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
    val errorText: String? = null
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
    private var availableCards: List<CharacterCard> = emptyList()
    private var activeConversationBundle: ConversationWithMessages? = null
    private var activeConversationJob: Job? = null
    private var lastAppliedAgentSettings: AgentSettings? = null
    private var lastDebugEventTimestampMs: Long = 0L

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

    fun openSettingsDetail(section: SettingsSection) {
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Settings,
                secondaryScreen = SecondaryScreen.SettingsDetail(section),
                settings = current.settings.copy(errorText = null)
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

    fun getProactiveNextTriggerMs(): Long = proactiveController.getNextTriggerMs()

    fun getProactiveSentCount(): Int = proactiveController.getSentCount()

    suspend fun applyProviderSettings(
        selectedProviderType: ProviderType,
        providerConfigs: Map<ProviderType, ProviderConfig>
    ): String? {
        val normalizedConfigs = ProviderType.entries.associateWith { providerType ->
            (providerConfigs[providerType] ?: ProviderConfig.defaultsFor(providerType))
                .copy(providerType = providerType)
                .normalized()
        }
        val selectedConfig = normalizedConfigs.getValue(selectedProviderType)

        if (selectedConfig.baseUrl.isNotEmpty()) {
            val endpoint = buildChatCompletionsUrl(selectedConfig.baseUrl)
            if (endpoint.toHttpUrlOrNull() == null) {
                return setSettingsError("Base URL 格式不正确。")
            }
        }

        clearSettingsError()
        return persistSettingsChange(
            fallbackMessage = "保存 Provider 设置失败。"
        ) {
            appPreferencesRepository.saveProviderSettings(
                selectedProviderType = selectedProviderType,
                providerConfigs = normalizedConfigs
            )
        }
    }

    suspend fun applyPersonaSettings(
        personaName: String,
        personaDescription: String
    ): String? {
        clearSettingsError()
        return persistSettingsChange(
            fallbackMessage = "保存 Persona 设置失败。"
        ) {
            appPreferencesRepository.saveUserPersona(
                UserPersona(
                    displayName = personaName,
                    description = personaDescription
                ).normalized()
            )
        }
    }

    suspend fun applyProactiveSettings(settings: ProactiveSettings): String? {
        if (settings.enabled) {
            if (settings.minIntervalMinutes < 3) {
                return setSettingsError("主动消息最短间隔不能小于 3 分钟。")
            }
            if (settings.maxIntervalMinutes < settings.minIntervalMinutes) {
                return setSettingsError("主动消息最长间隔不能小于最短间隔。")
            }
            if (settings.maxCount < 1) {
                return setSettingsError("主动消息最多发送次数不能小于 1。")
            }
        }

        clearSettingsError()
        return persistSettingsChange(
            fallbackMessage = "保存主动消息设置失败。"
        ) {
            appPreferencesRepository.saveAgentSettings(
                activePreferences.agentSettings.copy(proactive = settings)
            )
        }
    }

    suspend fun applyDirectorSettings(settings: DirectorSettings): String? {
        clearSettingsError()
        return persistSettingsChange(
            fallbackMessage = "保存导演系统设置失败。"
        ) {
            appPreferencesRepository.saveAgentSettings(
                activePreferences.agentSettings.copy(director = settings)
            )
        }
    }

    suspend fun applyMemorySettings(settings: MemorySettings): String? {
        if (settings.enabled) {
            if (settings.triggerCount < 1) {
                return setSettingsError("记忆摘要触发阈值不能小于 1。")
            }
            if (settings.keepRecentCount < 1) {
                return setSettingsError("记忆摘要保留消息数不能小于 1。")
            }
        }

        clearSettingsError()
        return persistSettingsChange(
            fallbackMessage = "保存记忆摘要设置失败。"
        ) {
            appPreferencesRepository.saveAgentSettings(
                activePreferences.agentSettings.copy(memory = settings)
            )
        }
    }

    suspend fun applyDeveloperModeEnabled(enabled: Boolean): String? {
        clearSettingsError()
        return persistSettingsChange(
            fallbackMessage = "保存开发者模式设置失败。"
        ) {
            appPreferencesRepository.saveDeveloperMode(enabled)
        }
    }

    private suspend fun persistSettingsChange(
        fallbackMessage: String,
        save: suspend () -> Unit
    ): String? {
        return runCatching {
            save()
        }.fold(
            onSuccess = { null },
            onFailure = { throwable ->
                setSettingsError(throwable.message ?: fallbackMessage)
            }
        )
    }

    private fun clearSettingsError() {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(errorText = null))
        }
    }

    private fun setSettingsError(message: String): String {
        _uiState.update { current ->
            current.copy(settings = current.settings.copy(errorText = message))
        }
        return message
    }

    private fun syncSettingsState(
        currentErrorText: String?,
        preferences: AppPreferences
    ): SettingsUiState {
        val providerConfigs = ProviderType.entries.associateWith { providerType ->
            (preferences.providerConfigs[providerType] ?: ProviderConfig.defaultsFor(providerType))
                .copy(providerType = providerType)
        }
        val selectedConfig = providerConfigs.getValue(preferences.selectedProviderType)
        val agentSettings = preferences.agentSettings

        return SettingsUiState(
            providerType = preferences.selectedProviderType,
            providerConfigs = providerConfigs,
            apiKey = selectedConfig.apiKey,
            model = selectedConfig.model,
            baseUrl = selectedConfig.baseUrl,
            personaName = preferences.userPersona.displayName,
            personaDescription = preferences.userPersona.description,
            proactiveEnabled = agentSettings.proactive.enabled,
            proactiveMinIntervalMinutes = agentSettings.proactive.minIntervalMinutes,
            proactiveMaxIntervalMinutes = agentSettings.proactive.maxIntervalMinutes,
            proactiveMaxCount = agentSettings.proactive.maxCount,
            directorEnabled = agentSettings.director.enabled,
            directorSystemPrompt = agentSettings.director.systemPrompt,
            memoryEnabled = agentSettings.memory.enabled,
            memoryTriggerCount = agentSettings.memory.triggerCount,
            memoryKeepCount = agentSettings.memory.keepRecentCount,
            developerModeEnabled = preferences.developerModeEnabled,
            errorText = currentErrorText
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
                        errorText = "请先在设置中填写 AI Provider、API Key、模型和 Base URL。"
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
            sendUserMessageInternal(userContent = userContent)
        }
    }

    private suspend fun sendProactiveMessage(elapsedMs: Long): ProactiveTriggerResult {
        if (uiState.value.conversation.isSending) return ProactiveTriggerResult.RETRY_LATER
        val bundle = activeConversationBundle ?: return ProactiveTriggerResult.RETRY_LATER
        val normalizedConfig = activePreferences.providerConfig.normalized()
        if (!normalizedConfig.hasRequiredFields()) return ProactiveTriggerResult.RETRY_LATER
        if (!isProactiveControllerEnabled()) return ProactiveTriggerResult.RETRY_LATER

        val historyMessages = bundle.messages
        appendDebugEvent(
            conversationId = bundle.conversation.id,
            source = ConversationDebugSource.Agent,
            type = ConversationDebugType.ProactiveTriggered,
            title = "主动消息触发",
            summary = "距离上次消息约 ${(elapsedMs / 60_000L).coerceAtLeast(0L)} 分钟。",
            details = buildDetailSections(
                "触发信息" to "elapsedMs=$elapsedMs",
                "最近消息" to formatMessages(historyMessages)
            )
        )
        checkAndSummarizeIfNeeded(
            messages = historyMessages,
            conversationId = bundle.conversation.id,
            config = normalizedConfig,
            snapshot = bundle.conversation.characterSnapshot
        )

        val decision = analyzeProactiveDecision(
            snapshot = bundle.conversation.characterSnapshot,
            conversationId = bundle.conversation.id,
            historyMessages = historyMessages,
            elapsedMs = elapsedMs,
            config = normalizedConfig
        ) ?: return ProactiveTriggerResult.RETRY_LATER

        if (decision.action == ProactiveAction.WAIT_FOR_USER) {
            appendDebugEvent(
                conversationId = bundle.conversation.id,
                source = ConversationDebugSource.Agent,
                type = ConversationDebugType.ProactivePaused,
                title = "主动消息暂停",
                summary = "导演决定等待用户回复，不主动发送新消息。",
                details = buildDetailSections(
                    "导演决策" to formatProactiveDecision(decision)
                )
            )
            return ProactiveTriggerResult.PAUSE_UNTIL_USER_REPLY
        }

        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    isSending = true,
                    errorText = null,
                    statusText = null
                )
            )
        }

        val sent = sendAssistantResponse(
            activeBundle = bundle,
            normalizedConfig = normalizedConfig,
            historyMessages = historyMessages,
            userContent = null,
            guidance = decision.toGuidance(),
            proactiveInstruction = decision.toInstruction()
        )

        return if (sent) {
            ProactiveTriggerResult.SENT
        } else {
            ProactiveTriggerResult.RETRY_LATER
        }
    }

    private suspend fun sendUserMessageInternal(userContent: String) {
        val activeBundle = activeConversationBundle ?: return
        val normalizedConfig = activePreferences.providerConfig.normalized()
        val userMessage = ChatMessage(
            role = ChatRole.User,
            content = userContent,
            createdAt = System.currentTimeMillis()
        )
        val historyMessages = activeBundle.messages + userMessage

        appendDebugEvent(
            conversationId = activeBundle.conversation.id,
            source = ConversationDebugSource.System,
            type = ConversationDebugType.SendStarted,
            title = "用户发送消息",
            summary = userContent.take(60),
            details = buildDetailSections(
                "用户消息" to userContent
            )
        )

        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    optimisticUserMessage = userMessage,
                    draft = "",
                    isSending = true,
                    errorText = null,
                    statusText = null
                )
            )
        }

        checkAndSummarizeIfNeeded(
            messages = historyMessages,
            conversationId = activeBundle.conversation.id,
            config = normalizedConfig,
            snapshot = activeBundle.conversation.characterSnapshot
        )

        val guidance = analyzeDirectorGuidance(
            snapshot = activeBundle.conversation.characterSnapshot,
            conversationId = activeBundle.conversation.id,
            historyMessages = historyMessages,
            config = normalizedConfig
        )

        sendAssistantResponse(
            activeBundle = activeBundle,
            normalizedConfig = normalizedConfig,
            historyMessages = historyMessages,
            userContent = userContent,
            guidance = guidance
        )
    }

    private suspend fun analyzeDirectorGuidance(
        snapshot: CharacterCardSnapshot?,
        conversationId: Long,
        historyMessages: List<ChatMessage>,
        config: ProviderConfig
    ): DirectorGuidance? {
        if (!activeAgentSettings.director.enabled) {
            return null
        }
        appendDebugEvent(
            conversationId = conversationId,
            source = ConversationDebugSource.Director,
            type = ConversationDebugType.DirectorAnalysisStarted,
            title = "导演分析开始",
            summary = "分析最近 ${historyMessages.size} 条上下文消息。",
            details = buildDetailSections(
                "输入消息" to formatMessages(historyMessages)
            )
        )
        setDirectorAnalysisState(isAnalyzing = true, statusText = "导演分析中...")
        val result = directorService.analyze(
            snapshot = snapshot,
            emotionState = activeEmotionState,
            conversationMessages = historyMessages,
            config = config
        )
        setDirectorAnalysisState(isAnalyzing = false, statusText = null)
        return result.fold(
            onSuccess = { traced ->
                appendDebugEvent(
                    conversationId = conversationId,
                    source = ConversationDebugSource.Director,
                    type = ConversationDebugType.DirectorAnalysisSucceeded,
                    title = "导演分析完成",
                    summary = traced.trace.parsedSummary.ifBlank { "导演未返回结构化摘要。" },
                    details = formatAgentTraceDetails(traced.trace)
                )
                traced.value
            },
            onFailure = { throwable ->
                appendDebugEvent(
                    conversationId = conversationId,
                    source = ConversationDebugSource.Director,
                    type = ConversationDebugType.DirectorAnalysisFailed,
                    title = "导演分析失败",
                    summary = throwable.message ?: "导演分析失败。",
                    details = formatThrowableDetails(throwable)
                )
                null
            }
        )
    }

    private suspend fun analyzeProactiveDecision(
        snapshot: CharacterCardSnapshot?,
        conversationId: Long,
        historyMessages: List<ChatMessage>,
        elapsedMs: Long,
        config: ProviderConfig
    ): ProactiveDirectorDecision? {
        appendDebugEvent(
            conversationId = conversationId,
            source = ConversationDebugSource.Director,
            type = ConversationDebugType.ProactiveDecisionStarted,
            title = "主动导演分析开始",
            summary = "距离用户上次发言约 ${(elapsedMs / 60_000L).coerceAtLeast(0L)} 分钟。",
            details = buildDetailSections(
                "输入消息" to formatMessages(historyMessages)
            )
        )
        setDirectorAnalysisState(isAnalyzing = true, statusText = "主动消息判断中...")
        val result = directorService.analyzeProactive(
            snapshot = snapshot,
            emotionState = activeEmotionState,
            conversationMessages = historyMessages,
            elapsedMs = elapsedMs,
            config = config
        )
        setDirectorAnalysisState(isAnalyzing = false, statusText = null)
        return result.fold(
            onSuccess = { traced ->
                appendDebugEvent(
                    conversationId = conversationId,
                    source = ConversationDebugSource.Director,
                    type = ConversationDebugType.ProactiveDecisionSucceeded,
                    title = "主动导演分析完成",
                    summary = traced.trace.parsedSummary.ifBlank { formatProactiveDecision(traced.value) },
                    details = formatAgentTraceDetails(traced.trace)
                )
                traced.value
            },
            onFailure = { throwable ->
                appendDebugEvent(
                    conversationId = conversationId,
                    source = ConversationDebugSource.Director,
                    type = ConversationDebugType.ProactiveDecisionFailed,
                    title = "主动导演分析失败",
                    summary = throwable.message ?: "主动导演分析失败。",
                    details = formatThrowableDetails(throwable)
                )
                null
            }
        )
    }

    private fun setDirectorAnalysisState(isAnalyzing: Boolean, statusText: String?) {
        _uiState.update { current ->
            current.copy(
                conversation = current.conversation.copy(
                    isDirectorAnalyzing = isAnalyzing,
                    statusText = statusText
                )
            )
        }
    }

    private suspend fun sendAssistantResponse(
        activeBundle: ConversationWithMessages,
        normalizedConfig: ProviderConfig,
        historyMessages: List<ChatMessage>,
        userContent: String?,
        guidance: DirectorGuidance?,
        proactiveInstruction: ProactiveInstruction? = null
    ): Boolean {
        val requestMessages = promptComposer.compose(
            characterSnapshot = activeBundle.conversation.characterSnapshot,
            userPersona = activePreferences.userPersona,
            conversationMessages = historyMessages,
            directorGuidance = guidance,
            proactiveInstruction = proactiveInstruction
        )

        appendDebugEvent(
            conversationId = activeBundle.conversation.id,
            source = ConversationDebugSource.System,
            type = ConversationDebugType.PromptComposed,
            title = "Prompt 拼装完成",
            summary = summarizePrompt(requestMessages),
            details = buildDetailSections(
                "拼装结果" to formatMessages(requestMessages)
            )
        )
        appendDebugEvent(
            conversationId = activeBundle.conversation.id,
            source = ConversationDebugSource.System,
            type = ConversationDebugType.ChatRequestStarted,
            title = "主模型请求开始",
            summary = "模型 ${normalizedConfig.model}",
            details = buildDetailSections(
                "请求信息" to "URL=${buildChatCompletionsUrl(normalizedConfig.baseUrl)}\n模型=${normalizedConfig.model}"
            )
        )

        return chatProvider.send(
            messages = requestMessages,
            config = normalizedConfig
        ).fold(
            onSuccess = { response ->
                val assistantMessage = response.message
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
                }.fold(
                    onSuccess = {
                        appendDebugEvent(
                            conversationId = activeBundle.conversation.id,
                            source = ConversationDebugSource.System,
                            type = ConversationDebugType.ChatRequestSucceeded,
                            title = "主模型请求成功",
                            summary = assistantMessage.content.take(80),
                            details = formatChatRequestTraceDetails(response.trace)
                        )
                        if (userContent != null) {
                            proactiveController.updateLastMessageTimestamp(System.currentTimeMillis())
                            proactiveController.resetCount()
                            if (!isProactiveControllerEnabled()) {
                                proactiveController.stop()
                            }
                        }
                        _uiState.update { current ->
                            current.copy(
                                conversation = current.conversation.copy(
                                    isSending = false,
                                    optimisticUserMessage = null,
                                    errorText = null
                                )
                            )
                        }
                        updateEmotionAsync(
                            conversationId = activeBundle.conversation.id,
                            snapshot = activeBundle.conversation.characterSnapshot,
                            recentMessages = historyMessages + assistantMessage,
                            config = normalizedConfig
                        )
                        true
                    },
                    onFailure = { throwable ->
                        appendDebugEvent(
                            conversationId = activeBundle.conversation.id,
                            source = ConversationDebugSource.System,
                            type = ConversationDebugType.ChatRequestFailed,
                            title = "聊天记录保存失败",
                            summary = throwable.message ?: "保存聊天记录失败。",
                            details = formatThrowableDetails(throwable)
                        )
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
                        false
                    }
                )
            },
            onFailure = { throwable ->
                appendDebugEvent(
                    conversationId = activeBundle.conversation.id,
                    source = ConversationDebugSource.System,
                    type = ConversationDebugType.ChatRequestFailed,
                    title = "主模型请求失败",
                    summary = throwable.message ?: "请求失败。",
                    details = formatChatFailureDetails(throwable, requestMessages)
                )
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
                false
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

        appendDebugEvent(
            conversationId = conversationId,
            source = ConversationDebugSource.Agent,
            type = ConversationDebugType.MemorySummaryStarted,
            title = "记忆摘要开始",
            summary = "压缩 ${toSummarize.size} 条旧消息，保留最近 ${keepMessages.size} 条。",
            details = buildDetailSections(
                "待压缩消息" to formatMessages(toSummarize),
                "保留消息" to formatMessages(keepMessages)
            )
        )

        memorySummarizer.summarize(
            messagesToSummarize = toSummarize,
            snapshot = snapshot,
            config = config
        ).fold(
            onSuccess = { traced ->
                conversationRepository.replaceWithSummary(
                    conversationId = conversationId,
                    summaryText = traced.value,
                    keepRecentMessages = keepMessages
                )
                appendDebugEvent(
                    conversationId = conversationId,
                    source = ConversationDebugSource.Agent,
                    type = ConversationDebugType.MemorySummarySucceeded,
                    title = "记忆摘要完成",
                    summary = traced.value.take(80),
                    details = formatAgentTraceDetails(traced.trace)
                )
            },
            onFailure = { throwable ->
                appendDebugEvent(
                    conversationId = conversationId,
                    source = ConversationDebugSource.Agent,
                    type = ConversationDebugType.MemorySummaryFailed,
                    title = "记忆摘要失败",
                    summary = throwable.message ?: "记忆摘要失败。",
                    details = formatThrowableDetails(throwable)
                )
            }
        )
    }

    private fun updateEmotionAsync(
        conversationId: Long,
        snapshot: CharacterCardSnapshot?,
        recentMessages: List<ChatMessage>,
        config: ProviderConfig
    ) {
        viewModelScope.launch {
            appendDebugEvent(
                conversationId = conversationId,
                source = ConversationDebugSource.Agent,
                type = ConversationDebugType.EmotionUpdateStarted,
                title = "情绪更新开始",
                summary = "基于最近 ${recentMessages.size} 条消息重新评估。",
                details = buildDetailSections(
                    "最近消息" to formatMessages(recentMessages)
                )
            )
            emotionUpdater.update(
                currentState = activeEmotionState,
                snapshot = snapshot,
                recentMessages = recentMessages,
                config = config
            ).fold(
                onSuccess = { traced ->
                    val newState = traced.value
                    activeEmotionState = newState
                    conversationRepository.updateEmotionState(conversationId, newState)
                    appendDebugEvent(
                        conversationId = conversationId,
                        source = ConversationDebugSource.Agent,
                        type = ConversationDebugType.EmotionUpdateSucceeded,
                        title = "情绪更新完成",
                        summary = "好感度 ${newState.affection}，心情 ${newState.mood}",
                        details = formatAgentTraceDetails(traced.trace)
                    )
                    _uiState.update { current ->
                        current.copy(
                            conversation = current.conversation.copy(
                                emotionState = newState
                            )
                        )
                    }
                },
                onFailure = { throwable ->
                    appendDebugEvent(
                        conversationId = conversationId,
                        source = ConversationDebugSource.Agent,
                        type = ConversationDebugType.EmotionUpdateFailed,
                        title = "情绪更新失败",
                        summary = throwable.message ?: "情绪更新失败。",
                        details = formatThrowableDetails(throwable)
                    )
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
                val agentSettingsChanged = lastAppliedAgentSettings != preferences.agentSettings
                lastAppliedAgentSettings = preferences.agentSettings
                if (agentSettingsChanged) {
                    activeAgentSettings = preferences.agentSettings
                    (directorService as? OpenAiCompatibleDirectorService)
                        ?.updateSystemPrompt(preferences.agentSettings.director.systemPrompt)
                    if (isProactiveControllerEnabled(preferences.agentSettings)) {
                        val lastTs = activeConversationBundle?.conversation?.updatedAt
                            ?: System.currentTimeMillis()
                        proactiveController.start(preferences.agentSettings.proactive, lastTs)
                    } else {
                        proactiveController.stop()
                    }
                }
                _uiState.update { current ->
                    current.copy(
                        conversation = current.conversation.copy(
                            hasValidConfig = normalizedConfig.hasRequiredFields()
                        ),
                        settings = syncSettingsState(
                            currentErrorText = current.settings.errorText,
                            preferences = preferences
                        )
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

                        is SecondaryScreen.SettingsDetail -> activeSecondary
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
                    debugEvents = emptyList(),
                    draft = "",
                    isSending = false,
                    emotionState = EmotionState(),
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
                    if (isProactiveControllerEnabled() && !proactiveController.isRunning()) {
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
                            debugEvents = bundle?.debugEvents.orEmpty(),
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

    private suspend fun appendDebugEvent(
        conversationId: Long,
        source: ConversationDebugSource,
        type: ConversationDebugType,
        title: String,
        summary: String,
        details: String = ""
    ) {
        runCatching {
            conversationRepository.appendDebugEvent(
                conversationId = conversationId,
                source = source,
                type = type,
                title = title,
                summary = summary,
                details = details,
                createdAt = nextDebugEventTimestamp()
            )
        }
    }

    private fun nextDebugEventTimestamp(): Long {
        val now = System.currentTimeMillis()
        return if (now > lastDebugEventTimestampMs) {
            lastDebugEventTimestampMs = now
            now
        } else {
            (lastDebugEventTimestampMs + 1L).also { lastDebugEventTimestampMs = it }
        }
    }

    private fun summarizePrompt(messages: List<ChatMessage>): String {
        val systemCount = messages.count { it.role == ChatRole.System }
        val userCount = messages.count { it.role == ChatRole.User }
        val assistantCount = messages.count { it.role == ChatRole.Assistant }
        return "system $systemCount 条，user $userCount 条，assistant $assistantCount 条，总计 ${messages.size} 条。"
    }

    private fun formatDirectorGuidance(guidance: DirectorGuidance): String {
        val parts = buildList {
            if (guidance.mood.isNotBlank()) add("氛围：${guidance.mood}")
            if (guidance.topicDirection.isNotBlank()) add("话题方向：${guidance.topicDirection}")
            if (guidance.pursue.isNotBlank()) add("推进：${guidance.pursue}")
            if (guidance.avoid.isNotBlank()) add("避免：${guidance.avoid}")
        }
        if (parts.isNotEmpty()) {
            return parts.joinToString("，")
        }
        val rawOutput = guidance.rawJson.trim()
        return rawOutput.ifBlank { "导演未返回可解析内容。" }
    }

    private fun formatProactiveDecision(decision: ProactiveDirectorDecision): String {
        return buildList {
            add("动作：${decision.action.wireName}")
            if (decision.mood.isNotBlank()) add("氛围：${decision.mood}")
            if (decision.topicDirection.isNotBlank()) add("话题方向：${decision.topicDirection}")
            if (decision.pursue.isNotBlank()) add("推进：${decision.pursue}")
            if (decision.avoid.isNotBlank()) add("避免：${decision.avoid}")
            if (decision.timeCue.isNotBlank()) add("时间表达：${decision.timeCue}")
        }.joinToString("，")
    }

    private fun formatMessages(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) {
            return "（无）"
        }
        return messages.joinToString("\n\n") { message ->
            "[${message.role.wireName}] ${message.content}"
        }
    }

    private fun formatAgentTraceDetails(trace: io.github.c1921.realchat.model.AgentExecutionTrace): String {
        return buildDetailSections(
            "系统提示词" to trace.systemPrompt,
            "请求消息" to formatMessages(trace.requestMessages),
            "原始输出" to trace.rawOutput,
            "解析摘要" to trace.parsedSummary
        )
    }

    private fun formatThrowableDetails(throwable: Throwable): String {
        val traceDetails = (throwable as? AgentExecutionException)?.trace?.let(::formatAgentTraceDetails)
        val chatTraceDetails = (throwable as? ChatRequestException)?.trace?.let(::formatChatRequestTraceDetails)
        return buildDetailSections(
            "错误信息" to (throwable.message ?: throwable::class.java.simpleName),
            "Trace" to traceDetails,
            "请求 Trace" to chatTraceDetails
        )
    }

    private fun formatChatRequestTraceDetails(trace: ChatRequestTrace): String {
        return buildDetailSections(
            "请求信息" to "URL=${trace.requestUrl}\n模型=${trace.model}",
            "请求消息" to formatMessages(trace.requestMessages),
            "原始响应体" to trace.rawResponseBody,
            "提取回复" to trace.responseContent
        )
    }

    private fun formatChatFailureDetails(
        throwable: Throwable,
        fallbackMessages: List<ChatMessage>
    ): String {
        val trace = (throwable as? ChatRequestException)?.trace
        return buildDetailSections(
            "错误信息" to (throwable.message ?: throwable::class.java.simpleName),
            "请求信息" to trace?.let { "URL=${it.requestUrl}\n模型=${it.model}" },
            "请求消息" to formatMessages(trace?.requestMessages ?: fallbackMessages),
            "原始响应体" to trace?.rawResponseBody
        )
    }

    private fun buildDetailSections(vararg sections: Pair<String, String?>): String {
        return sections.mapNotNull { (title, content) ->
            content?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { "$title\n$it" }
        }.joinToString("\n\n")
    }

    private fun isProactiveControllerEnabled(settings: AgentSettings = activeAgentSettings): Boolean {
        return settings.proactive.enabled && settings.director.enabled
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
