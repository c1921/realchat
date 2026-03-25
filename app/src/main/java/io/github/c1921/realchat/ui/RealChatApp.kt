package io.github.c1921.realchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole

@Composable
fun RealChatApp(
    uiState: MainUiState,
    onOpenSettings: () -> Unit,
    onBackToChat: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSaveSettings: () -> Unit
) {
    when (uiState.currentScreen) {
        AppScreen.Chat -> ChatScreen(
            chat = uiState.chat,
            settings = uiState.settings,
            onOpenSettings = onOpenSettings,
            onDraftChange = onDraftChange,
            onSendMessage = onSendMessage
        )

        AppScreen.Settings -> SettingsScreen(
            settings = uiState.settings,
            onBackToChat = onBackToChat,
            onApiKeyChange = onApiKeyChange,
            onModelChange = onModelChange,
            onBaseUrlChange = onBaseUrlChange,
            onSaveSettings = onSaveSettings
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    chat: ChatUiState,
    settings: SettingsUiState,
    onOpenSettings: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(chat.messages.size) {
        if (chat.messages.isNotEmpty()) {
            listState.animateScrollToItem(chat.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RealChat") },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text("设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!chat.hasValidConfig) {
                SupportText(
                    text = "请先到设置页填写 API Key、模型和 Base URL。",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                SupportText(
                    text = "当前模型：${settings.model}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!chat.errorText.isNullOrBlank()) {
                SupportText(
                    text = chat.errorText,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (chat.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (chat.hasValidConfig) {
                            "输入一条消息开始对话。"
                        } else {
                            "尚未配置 DeepSeek 接口。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chat.messages) { message ->
                        MessageCard(message = message)
                    }
                }
            }

            OutlinedTextField(
                value = chat.draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                enabled = chat.hasValidConfig && !chat.isSending,
                label = { Text("消息") },
                placeholder = { Text("输入你想说的话") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onSendMessage,
                    enabled = chat.hasValidConfig &&
                        chat.draft.isNotBlank() &&
                        !chat.isSending
                ) {
                    Text(if (chat.isSending) "发送中..." else "发送")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: SettingsUiState,
    onBackToChat: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSaveSettings: () -> Unit
) {
    BackHandler(onBack = onBackToChat)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    TextButton(onClick = onBackToChat) {
                        Text("返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            OutlinedTextField(
                value = settings.model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型") },
                singleLine = true
            )

            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            SupportText(
                text = "DeepSeek 默认 Base URL：https://api.deepseek.com",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!settings.errorText.isNullOrBlank()) {
                SupportText(
                    text = settings.errorText,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!settings.statusText.isNullOrBlank()) {
                SupportText(
                    text = settings.statusText,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = onSaveSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatMessage
) {
    val isUser = message.role == ChatRole.User
    val cardColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isUser) "你" else "DeepSeek",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = message.content, color = Color.Unspecified)
        }
    }
}

@Composable
private fun SupportText(
    text: String,
    color: Color
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}
