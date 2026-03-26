package io.github.c1921.realchat

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.c1921.realchat.ui.ChatViewModel
import io.github.c1921.realchat.ui.RealChatApp
import io.github.c1921.realchat.ui.theme.RealChatTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<ChatViewModel> {
        ChatViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RealChatTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                RealChatApp(
                    uiState = uiState,
                    onOpenScreen = viewModel::openScreen,
                    onOpenConversationDetail = viewModel::openConversationDetail,
                    onCloseSecondaryScreen = viewModel::closeSecondaryScreen,
                    onDraftChange = viewModel::updateDraft,
                    onSendMessage = viewModel::sendMessage,
                    onShowCreateConversationDialog = viewModel::showCreateConversationDialog,
                    onDismissCreateConversationDialog = viewModel::dismissCreateConversationDialog,
                    onPendingConversationCardIdChange = viewModel::updatePendingConversationCardId,
                    onCreateConversation = viewModel::createConversation,
                    onDeleteSelectedConversation = viewModel::deleteSelectedConversation,
                    onOpenCreateCharacterEditor = viewModel::openCreateCharacterEditor,
                    onOpenEditCharacterEditor = viewModel::openEditCharacterEditor,
                    onCancelCharacterEditing = viewModel::cancelCharacterEditing,
                    onCharacterEditorChange = viewModel::updateCharacterEditor,
                    onSaveCharacterCard = viewModel::saveCharacterCard,
                    onDuplicateCharacterCard = viewModel::duplicateCharacterCard,
                    onDeleteCharacterCard = viewModel::deleteCharacterCard,
                    onImportCharacterCard = viewModel::importCharacterCard,
                    onReportCharacterError = viewModel::reportCharacterError,
                    onRequestCharacterCardExport = viewModel::requestCharacterCardExport,
                    onClearPendingCharacterCardExport = viewModel::clearPendingCharacterCardExport,
                    onCharacterCardExportCompleted = viewModel::onCharacterCardExportCompleted,
                    onProviderTypeChange = viewModel::updateProviderType,
                    onApiKeyChange = viewModel::updateApiKey,
                    onModelChange = viewModel::updateModel,
                    onBaseUrlChange = viewModel::updateBaseUrl,
                    onPersonaNameChange = viewModel::updatePersonaName,
                    onPersonaDescriptionChange = viewModel::updatePersonaDescription,
                    onSaveSettings = viewModel::saveSettings
                )
            }
        }
    }
}
