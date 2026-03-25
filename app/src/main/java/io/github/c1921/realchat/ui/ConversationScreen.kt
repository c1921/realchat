package io.github.c1921.realchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard

@Composable
fun ConversationScreen(
    conversation: ConversationUiState,
    cards: List<CharacterCard>,
    modifier: Modifier = Modifier,
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
    onDeleteSelectedConversation: () -> Unit
) {
    val messageListState = rememberLazyListState()

    LaunchedEffect(conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) {
            messageListState.animateScrollToItem(conversation.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            title = "会话",
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowCreateConversationDialog) {
                        Text("新建")
                    }
                    OutlinedButton(
                        onClick = onShowRenameConversationDialog,
                        enabled = conversation.selectedConversationId != null
                    ) {
                        Text("重命名")
                    }
                    OutlinedButton(
                        onClick = onDeleteSelectedConversation,
                        enabled = conversation.selectedConversationId != null
                    ) {
                        Text("删除")
                    }
                }
            }
        )

        if (!conversation.hasValidConfig) {
            SupportText(
                text = "请先到设置页填写 API Key、模型和 Base URL。",
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!conversation.errorText.isNullOrBlank()) {
            SupportText(
                text = conversation.errorText,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!conversation.statusText.isNullOrBlank()) {
            SupportText(
                text = conversation.statusText,
                color = MaterialTheme.colorScheme.primary
            )
        }

        ConversationList(
            conversation = conversation,
            onSelectConversation = onSelectConversation
        )

        val selectedConversation = conversation.selectedConversation()
        if (selectedConversation == null) {
            EmptyPanel("暂无会话。先创建一个带角色卡的新会话。")
        } else {
            SupportText(
                text = "当前角色：${selectedConversation.characterSnapshot?.effectiveName().orEmpty().ifBlank { "未绑定" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (conversation.messages.isEmpty()) {
                EmptyPanel("当前会话还没有消息。")
            } else {
                LazyColumn(
                    state = messageListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(conversation.messages) { message ->
                        MessageCard(message = message)
                    }
                }
            }
        }

        OutlinedTextField(
            value = conversation.draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            label = { Text("消息") },
            placeholder = { Text("输入你想说的话") },
            enabled = conversation.selectedConversationId != null && !conversation.isSending
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onSendMessage,
                enabled = conversation.selectedConversationId != null &&
                    conversation.draft.isNotBlank() &&
                    !conversation.isSending &&
                    conversation.hasValidConfig
            ) {
                Text(if (conversation.isSending) "发送中..." else "发送")
            }
        }
    }

    if (conversation.showCreateDialog) {
        CreateConversationDialog(
            cards = cards,
            selectedCardId = conversation.pendingCharacterCardId,
            title = conversation.pendingConversationTitle,
            onDismiss = onDismissCreateConversationDialog,
            onTitleChange = onPendingConversationTitleChange,
            onSelectCard = onPendingConversationCardIdChange,
            onConfirm = onCreateConversation
        )
    }

    if (conversation.showRenameDialog) {
        RenameConversationDialog(
            title = conversation.pendingRenameTitle,
            onDismiss = onDismissRenameConversationDialog,
            onTitleChange = onPendingRenameTitleChange,
            onConfirm = onRenameSelectedConversation
        )
    }
}

@Composable
private fun ConversationList(
    conversation: ConversationUiState,
    onSelectConversation: (Long) -> Unit
) {
    if (conversation.conversations.isEmpty()) {
        EmptyPanel("还没有任何会话。")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(conversation.conversations, key = { it.id }) { item ->
            val selected = item.id == conversation.selectedConversationId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectConversation(item.id) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = item.effectiveTitle(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = item.characterSnapshot?.effectiveName().orEmpty().ifBlank { "未绑定角色卡" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateConversationDialog(
    cards: List<CharacterCard>,
    selectedCardId: Long?,
    title: String,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onSelectCard: (Long) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("会话标题，可留空") }
                )
                cards.forEach { card ->
                    val selected = selectedCardId == card.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCard(card.id) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .padding(12.dp)
                        ) {
                            Text(card.toSnapshot().effectiveName())
                            if (card.description.isNotBlank()) {
                                Text(
                                    text = card.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = cards.isNotEmpty() && selectedCardId != null
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RenameConversationDialog(
    title: String,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun MessageCard(message: ChatMessage) {
    val isUser = message.role == ChatRole.User
    val cardColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = when (message.role) {
                    ChatRole.User -> "你"
                    ChatRole.Assistant -> "角色"
                    ChatRole.System -> "系统"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = message.content, color = Color.Unspecified)
        }
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ScreenTitle(
    title: String,
    actions: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        actions()
    }
}

@Composable
internal fun SupportText(
    text: String,
    color: Color
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}
