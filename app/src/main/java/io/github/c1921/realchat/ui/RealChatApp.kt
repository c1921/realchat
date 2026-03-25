package io.github.c1921.realchat.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun RealChatApp(
    uiState: MainUiState,
    onOpenScreen: (AppScreen) -> Unit,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onShowCreateConversationDialog: () -> Unit,
    onDismissCreateConversationDialog: () -> Unit,
    onPendingConversationTitleChange: (String) -> Unit,
    onPendingConversationCardIdChange: (Long) -> Unit,
    onCreateConversation: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onShowRenameConversationDialog: () -> Unit,
    onDismissRenameConversationDialog: () -> Unit,
    onPendingRenameTitleChange: (String) -> Unit,
    onRenameSelectedConversation: () -> Unit,
    onDeleteSelectedConversation: () -> Unit,
    onOpenCreateCharacterEditor: () -> Unit,
    onOpenEditCharacterEditor: (Long) -> Unit,
    onCancelCharacterEditing: () -> Unit,
    onCharacterEditorChange: (CharacterEditorField, String) -> Unit,
    onSaveCharacterCard: () -> Unit,
    onDuplicateCharacterCard: (Long) -> Unit,
    onDeleteCharacterCard: (Long) -> Unit,
    onImportCharacterCard: (String) -> Unit,
    onReportCharacterError: (String) -> Unit,
    onRequestCharacterCardExport: (Long) -> Unit,
    onClearPendingCharacterCardExport: () -> Unit,
    onCharacterCardExportCompleted: (String?) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onPersonaNameChange: (String) -> Unit,
    onPersonaDescriptionChange: (String) -> Unit,
    onSaveSettings: () -> Unit
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        readTextFromUri(context, uri)
            .onSuccess(onImportCharacterCard)
            .onFailure {
                onReportCharacterError("读取角色卡文件失败。")
            }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pendingExport = uiState.characters.pendingExport
        if (pendingExport == null) {
            return@rememberLauncherForActivityResult
        }

        if (uri == null) {
            onClearPendingCharacterCardExport()
            return@rememberLauncherForActivityResult
        }

        writeTextToUri(context, uri, pendingExport.content)
            .onSuccess {
                onCharacterCardExportCompleted(null)
            }
            .onFailure {
                onCharacterCardExportCompleted("写入导出文件失败。")
            }
    }

    LaunchedEffect(uiState.characters.pendingExport?.fileName) {
        uiState.characters.pendingExport?.let { payload ->
            exportLauncher.launch(payload.fileName)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = uiState.currentScreen == screen,
                        onClick = { onOpenScreen(screen) },
                        label = { Text(screen.label()) },
                        icon = { Text(screen.iconLabel()) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (uiState.currentScreen) {
            AppScreen.Conversations -> ConversationScreen(
                conversation = uiState.conversation,
                cards = uiState.characters.cards,
                modifier = Modifier.padding(innerPadding),
                onDraftChange = onDraftChange,
                onSendMessage = onSendMessage,
                onShowCreateConversationDialog = onShowCreateConversationDialog,
                onDismissCreateConversationDialog = onDismissCreateConversationDialog,
                onPendingConversationTitleChange = onPendingConversationTitleChange,
                onPendingConversationCardIdChange = onPendingConversationCardIdChange,
                onCreateConversation = onCreateConversation,
                onSelectConversation = onSelectConversation,
                onShowRenameConversationDialog = onShowRenameConversationDialog,
                onDismissRenameConversationDialog = onDismissRenameConversationDialog,
                onPendingRenameTitleChange = onPendingRenameTitleChange,
                onRenameSelectedConversation = onRenameSelectedConversation,
                onDeleteSelectedConversation = onDeleteSelectedConversation
            )

            AppScreen.Characters -> CharacterCardsRoute(
                state = uiState.characters,
                modifier = Modifier.padding(innerPadding),
                onOpenCreateCharacterEditor = onOpenCreateCharacterEditor,
                onOpenEditCharacterEditor = onOpenEditCharacterEditor,
                onCancelCharacterEditing = onCancelCharacterEditing,
                onCharacterEditorChange = onCharacterEditorChange,
                onSaveCharacterCard = onSaveCharacterCard,
                onDuplicateCharacterCard = onDuplicateCharacterCard,
                onDeleteCharacterCard = onDeleteCharacterCard,
                onImportCharacterCardClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                },
                onRequestCharacterCardExport = onRequestCharacterCardExport
            )

            AppScreen.Settings -> SettingsScreen(
                settings = uiState.settings,
                modifier = Modifier.padding(innerPadding),
                onApiKeyChange = onApiKeyChange,
                onModelChange = onModelChange,
                onBaseUrlChange = onBaseUrlChange,
                onPersonaNameChange = onPersonaNameChange,
                onPersonaDescriptionChange = onPersonaDescriptionChange,
                onSaveSettings = onSaveSettings
            )
        }
    }
}

private fun AppScreen.label(): String {
    return when (this) {
        AppScreen.Conversations -> "会话"
        AppScreen.Characters -> "角色卡"
        AppScreen.Settings -> "设置"
    }
}

private fun AppScreen.iconLabel(): String {
    return when (this) {
        AppScreen.Conversations -> "聊"
        AppScreen.Characters -> "卡"
        AppScreen.Settings -> "设"
    }
}

private fun readTextFromUri(
    context: Context,
    uri: Uri
): Result<String> {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: error("无法打开文件。")
    }
}

private fun writeTextToUri(
    context: Context,
    uri: Uri,
    text: String
): Result<Unit> {
    return runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(text)
        } ?: error("无法写入文件。")
    }
}
