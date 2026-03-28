package io.github.c1921.realchat.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.c1921.realchat.model.DirectorSettings
import io.github.c1921.realchat.model.MemorySettings
import io.github.c1921.realchat.model.ProactiveSettings
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.ProviderType

@Composable
fun RealChatApp(
    uiState: MainUiState,
    onOpenScreen: (AppScreen) -> Unit,
    onOpenConversationDetail: (Long) -> Unit,
    onCloseSecondaryScreen: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onShowCreateConversationDialog: () -> Unit,
    onDismissCreateConversationDialog: () -> Unit,
    onPendingConversationCardIdChange: (Long) -> Unit,
    onCreateConversation: () -> Unit,
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
    onOpenSettingsDetail: (SettingsSection) -> Unit,
    onApplyProviderSettings: suspend (ProviderType, Map<ProviderType, ProviderConfig>) -> String?,
    onApplyPersonaSettings: suspend (String, String) -> String?,
    onApplyProactiveSettings: suspend (ProactiveSettings) -> String?,
    onApplyDirectorSettings: suspend (DirectorSettings) -> String?,
    onApplyMemorySettings: suspend (MemorySettings) -> String?,
    onApplyDeveloperModeEnabled: suspend (Boolean) -> String?,
    onGetProactiveNextTriggerMs: () -> Long,
    onGetProactiveSentCount: () -> Int
) {
    val chatDetailScreen = uiState.secondaryScreen as? SecondaryScreen.ChatDetail
    val settingsDetailScreen = uiState.secondaryScreen as? SecondaryScreen.SettingsDetail
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

    if (chatDetailScreen != null) {
        BackHandler(onBack = onCloseSecondaryScreen)
    }

    Scaffold(
        bottomBar = {
            if (uiState.secondaryScreen == null) {
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
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val conversationScreenPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = 0.dp,
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding()
        )

        when {
            chatDetailScreen != null -> ChatDetailScreen(
                conversation = uiState.conversation,
                settings = uiState.settings,
                onGetProactiveNextTriggerMs = onGetProactiveNextTriggerMs,
                onGetProactiveSentCount = onGetProactiveSentCount,
                modifier = Modifier.padding(conversationScreenPadding),
                onBack = onCloseSecondaryScreen,
                onDraftChange = onDraftChange,
                onSendMessage = onSendMessage
            )

            uiState.currentScreen == AppScreen.Conversations -> ConversationHomeScreen(
                conversation = uiState.conversation,
                cards = uiState.characters.cards,
                modifier = Modifier.padding(conversationScreenPadding),
                onOpenConversationDetail = onOpenConversationDetail,
                onShowCreateConversationDialog = onShowCreateConversationDialog,
                onDismissCreateConversationDialog = onDismissCreateConversationDialog,
                onPendingConversationCardIdChange = onPendingConversationCardIdChange,
                onCreateConversation = onCreateConversation,
                onDeleteSelectedConversation = onDeleteSelectedConversation
            )

            uiState.currentScreen == AppScreen.Characters -> CharacterCardsRoute(
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

            else -> SettingsScreen(
                settings = uiState.settings,
                activeSection = settingsDetailScreen?.section,
                modifier = Modifier.padding(innerPadding),
                onOpenSection = onOpenSettingsDetail,
                onCloseSection = onCloseSecondaryScreen,
                onApplyProviderSettings = onApplyProviderSettings,
                onApplyPersonaSettings = onApplyPersonaSettings,
                onApplyProactiveSettings = onApplyProactiveSettings,
                onApplyDirectorSettings = onApplyDirectorSettings,
                onApplyMemorySettings = onApplyMemorySettings,
                onApplyDeveloperModeEnabled = onApplyDeveloperModeEnabled
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
