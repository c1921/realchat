package io.github.c1921.realchat.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.realchat.data.chat.AppDatabase
import io.github.c1921.realchat.data.chat.ChatHistoryRepository
import io.github.c1921.realchat.data.chat.ChatProvider
import io.github.c1921.realchat.data.chat.DeepSeekChatProvider
import io.github.c1921.realchat.data.chat.RoomChatHistoryRepository
import io.github.c1921.realchat.data.chat.buildChatCompletionsUrl
import io.github.c1921.realchat.data.settings.DataStoreSettingsRepository
import io.github.c1921.realchat.data.settings.SettingsRepository
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.ProviderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class AppScreen {
    Chat,
    Settings
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val errorText: String? = null,
    val hasValidConfig: Boolean = false
)

data class SettingsUiState(
    val apiKey: String = "",
    val model: String = ProviderConfig.DEFAULT_MODEL,
    val baseUrl: String = ProviderConfig.DEFAULT_BASE_URL,
    val errorText: String? = null,
    val statusText: String? = null
)

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.Chat,
    val chat: ChatUiState = ChatUiState(),
    val settings: SettingsUiState = SettingsUiState()
)

class ChatViewModel(
    private val settingsRepository: SettingsRepository,
    private val chatProvider: ChatProvider,
    private val chatHistoryRepository: ChatHistoryRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var activeConfig: ProviderConfig = ProviderConfig()
    private var hasRestoredChatState = false
    private val draftWriteMutex = Mutex()

    init {
        viewModelScope.launch {
            settingsRepository.observeConfig().collect { config ->
                activeConfig = config.normalized()
                _uiState.update { current ->
                    current.copy(
                        chat = current.chat.copy(
                            hasValidConfig = activeConfig.hasRequiredFields()
                        ),
                        settings = current.settings.copy(
                            apiKey = config.apiKey,
                            model = config.model,
                            baseUrl = config.baseUrl
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            chatHistoryRepository.observeState().collect { persistedState ->
                _uiState.update { current ->
                    val nextChatState = current.chat.copy(
                        messages = persistedState.messages,
                        draft = persistedState.draft
                    )

                    if (!hasRestoredChatState) {
                        hasRestoredChatState = true
                        current.copy(
                            chat = nextChatState.copy(
                                isSending = false,
                                errorText = null
                            ),
                            settings = current.settings.copy(statusText = null)
                        )
                    } else {
                        current.copy(chat = nextChatState)
                    }
                }
            }
        }
    }

    fun openSettings() {
        _uiState.update { current ->
            current.copy(currentScreen = AppScreen.Settings)
        }
    }

    fun backToChat() {
        _uiState.update { current ->
            current.copy(currentScreen = AppScreen.Chat)
        }
    }

    fun updateDraft(draft: String) {
        _uiState.update { current ->
            current.copy(
                chat = current.chat.copy(
                    draft = draft,
                    errorText = null
                )
            )
        }

        viewModelScope.launch {
            runCatching {
                draftWriteMutex.withLock {
                    chatHistoryRepository.updateDraft(draft)
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

    fun saveSettings() {
        val form = uiState.value.settings
        val config = ProviderConfig(
            apiKey = form.apiKey,
            model = form.model,
            baseUrl = form.baseUrl
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
            settingsRepository.saveConfig(config)
            _uiState.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        apiKey = config.apiKey,
                        model = config.model,
                        baseUrl = config.baseUrl,
                        errorText = null,
                        statusText = "设置已保存。"
                    ),
                    chat = current.chat.copy(errorText = null)
                )
            }
        }
    }

    fun sendMessage() {
        val currentState = uiState.value
        if (currentState.chat.isSending) {
            return
        }

        if (!activeConfig.hasRequiredFields()) {
            _uiState.update { current ->
                current.copy(
                    chat = current.chat.copy(
                        errorText = "请先在设置中保存 API Key、模型和 Base URL。"
                    )
                )
            }
            return
        }

        val userContent = currentState.chat.draft.trim()
        if (userContent.isEmpty()) {
            return
        }

        val requestMessages = currentState.chat.messages + ChatMessage(
            role = ChatRole.User,
            content = userContent
        )

        _uiState.update { current ->
            current.copy(
                chat = current.chat.copy(
                    isSending = true,
                    errorText = null
                )
            )
        }

        viewModelScope.launch {
            val result = chatProvider.send(
                messages = requestMessages,
                config = activeConfig
            )

            result.fold(
                onSuccess = { assistantMessage ->
                    runCatching {
                        chatHistoryRepository.appendSuccessfulExchange(
                            userContent = userContent,
                            assistantMessage = assistantMessage
                        )
                    }.onSuccess {
                        _uiState.update { current ->
                            current.copy(
                                chat = current.chat.copy(
                                    messages = requestMessages + assistantMessage,
                                    draft = "",
                                    isSending = false,
                                    errorText = null
                                )
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update { current ->
                            current.copy(
                                chat = current.chat.copy(
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
                            chat = current.chat.copy(
                                isSending = false,
                                errorText = throwable.message ?: "请求失败。"
                            )
                        )
                    }
                }
            )
        }
    }

    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = DataStoreSettingsRepository(context)
            val provider = DeepSeekChatProvider()
            val database = AppDatabase.getInstance(context)
            val chatHistoryRepository = RoomChatHistoryRepository(database.chatHistoryDao())
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                settingsRepository = repository,
                chatProvider = provider,
                chatHistoryRepository = chatHistoryRepository
            ) as T
        }
    }
}
