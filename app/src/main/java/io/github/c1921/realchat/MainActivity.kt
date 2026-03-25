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
                    onOpenSettings = viewModel::openSettings,
                    onBackToChat = viewModel::backToChat,
                    onDraftChange = viewModel::updateDraft,
                    onSendMessage = viewModel::sendMessage,
                    onApiKeyChange = viewModel::updateApiKey,
                    onModelChange = viewModel::updateModel,
                    onBaseUrlChange = viewModel::updateBaseUrl,
                    onSaveSettings = viewModel::saveSettings
                )
            }
        }
    }
}
