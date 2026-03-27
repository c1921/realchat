@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.c1921.realchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.c1921.realchat.model.ChatMessage
import io.github.c1921.realchat.model.ChatRole
import io.github.c1921.realchat.model.CharacterCard
import io.github.c1921.realchat.model.ConversationListItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CONVERSATION_LIST_TAG = "conversation_list"
private const val MESSAGE_LIST_TAG = "message_list"
private const val STATUS_SECTION_TAG = "conversation_status_section"
private const val CHAT_COMPOSER_INPUT_TAG = "chat_composer_input"
private const val CHAT_COMPOSER_SEND_TAG = "chat_composer_send"
private const val USER_BUBBLE_TAG = "message_bubble_user"
private const val ASSISTANT_BUBBLE_TAG = "message_bubble_assistant"
private const val SYSTEM_BANNER_TAG = "message_banner_system"

private val SameDayTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

private val MonthDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA)

@Composable
fun ConversationHomeScreen(
    conversation: ConversationUiState,
    cards: List<CharacterCard>,
    modifier: Modifier = Modifier,
    onOpenConversationDetail: (Long) -> Unit,
    onShowCreateConversationDialog: () -> Unit,
    onDismissCreateConversationDialog: () -> Unit,
    onPendingConversationCardIdChange: (Long) -> Unit,
    onCreateConversation: () -> Unit,
    onDeleteSelectedConversation: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("会话") },
                actions = {
                    IconButton(onClick = onShowCreateConversationDialog) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "新建会话"
                        )
                    }
                    if (conversation.selectedConversationId != null) {
                        IconButton(onClick = onDeleteSelectedConversation) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "删除会话"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ConversationNoticeSection(
                conversation = conversation,
                invalidConfigText = "请先到设置页填写 API Key、模型和 Base URL。"
            )
            ConversationList(
                conversation = conversation,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                onOpenConversationDetail = onOpenConversationDetail
            )
        }
    }

    if (conversation.showCreateDialog) {
        CreateConversationDialog(
            cards = cards,
            selectedCardId = conversation.pendingCharacterCardId,
            onDismiss = onDismissCreateConversationDialog,
            onSelectCard = onPendingConversationCardIdChange,
            onConfirm = onCreateConversation
        )
    }
}

@Composable
fun ChatDetailScreen(
    conversation: ConversationUiState,
    settings: SettingsUiState,
    onGetProactiveNextTriggerMs: () -> Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    val messageListState = rememberLazyListState()
    val selectedConversation = conversation.selectedConversation()
    val roleName = selectedConversation?.characterSnapshot?.effectiveName()
        .orEmpty()
        .ifBlank { "未绑定角色" }

    LaunchedEffect(selectedConversation?.id, conversation.messages.size) {
        if (conversation.messages.isNotEmpty()) {
            messageListState.animateScrollToItem(conversation.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                title = {
                    Column {
                        ChatHeaderTitle(roleName = roleName)
                        val emotion = conversation.emotionState
                        val moodLabel = when {
                            emotion.mood > 0 -> "▲${emotion.mood}"
                            emotion.mood < 0 -> "▼${-emotion.mood}"
                            else -> "—"
                        }
                        Text(
                            text = "好感度 ${emotion.affection} | 心情 $moodLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            ChatComposer(
                draft = conversation.draft,
                enabled = conversation.selectedConversationId != null && !conversation.isSending,
                canSend = conversation.selectedConversationId != null &&
                    conversation.draft.isNotBlank() &&
                    conversation.hasValidConfig &&
                    !conversation.isSending,
                isSending = conversation.isSending,
                onDraftChange = onDraftChange,
                onSendMessage = onSendMessage
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ConversationNoticeSection(
                conversation = conversation,
                invalidConfigText = "请先在设置中保存 API Key、模型和 Base URL。"
            )

            if (settings.developerModeEnabled) {
                DevDebugPanel(
                    proactiveEnabled = settings.proactiveEnabled,
                    onGetNextTriggerMs = onGetProactiveNextTriggerMs
                )
            }

            when {
                selectedConversation == null -> {
                    EmptyPanel(
                        text = "暂无会话。",
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                conversation.messages.isEmpty() -> {
                    EmptyPanel(
                        text = "当前会话还没有消息。",
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                else -> {
                    MessageList(
                        messages = conversation.messages,
                        state = messageListState,
                        developerModeEnabled = settings.developerModeEnabled,
                        directorGuidanceHints = conversation.directorGuidanceHints,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationList(
    conversation: ConversationUiState,
    modifier: Modifier = Modifier,
    onOpenConversationDetail: (Long) -> Unit
) {
    if (conversation.conversationItems.isEmpty()) {
        EmptyPanel(
            text = "还没有任何会话。",
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CONVERSATION_LIST_TAG),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(
            items = conversation.conversationItems,
            key = { _, item -> item.conversation.id }
        ) { _, item ->
            ConversationListRow(
                item = item,
                selected = item.conversation.id == conversation.selectedConversationId,
                onClick = { onOpenConversationDetail(item.conversation.id) }
            )
        }
    }
}

@Composable
private fun ConversationListRow(
    item: ConversationListItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val roleName = item.conversation.characterSnapshot?.effectiveName()
        .orEmpty()
        .ifBlank { "未绑定角色" }
    val timestamp = formatConversationTimestamp(item.conversation.updatedAt)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversation_row_${item.conversation.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            Color.Transparent
        },
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ConversationAvatar(
                    label = avatarLabel(roleName),
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = roleName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (timestamp.isNotBlank()) {
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }
                Text(
                    text = item.summaryText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatHeaderTitle(
    roleName: String
) {
    Text(
        text = roleName,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    state: LazyListState,
    developerModeEnabled: Boolean = false,
    directorGuidanceHints: Map<Int, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(MESSAGE_LIST_TAG),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(messages) { index, message ->
            when (message.role) {
                ChatRole.System -> SystemMessageBanner(message = message)
                ChatRole.User, ChatRole.Assistant -> {
                    val previousRole = messages.getOrNull(index - 1)?.role
                    val nextRole = messages.getOrNull(index + 1)?.role
                    val guidanceText = if (developerModeEnabled && message.role == ChatRole.Assistant) {
                        directorGuidanceHints[index]
                    } else null
                    Column {
                        if (guidanceText != null) {
                            Text(
                                text = guidanceText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        MessageBubble(
                            message = message,
                            isGroupedWithPrevious = previousRole == message.role,
                            isGroupedWithNext = nextRole == message.role
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isGroupedWithPrevious: Boolean,
    isGroupedWithNext: Boolean
) {
    val isUser = message.role == ChatRole.User
    val bubbleTag = if (isUser) USER_BUBBLE_TAG else ASSISTANT_BUBBLE_TAG

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.82f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Surface(
                    modifier = Modifier.testTag(bubbleTag),
                    shape = bubbleShape(
                        isUser = isUser,
                        isGroupedWithPrevious = isGroupedWithPrevious,
                        isGroupedWithNext = isGroupedWithNext
                    ),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    tonalElevation = if (isUser) 1.dp else 0.dp
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemMessageBanner(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.testTag(SYSTEM_BANNER_TAG),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    enabled: Boolean,
    canSend: Boolean,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    Surface(
        modifier = Modifier.imePadding(),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(CHAT_COMPOSER_INPUT_TAG),
                    enabled = enabled,
                    minLines = 1,
                    maxLines = 5,
                    placeholder = { Text("输入你想说的话") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    )
                )

                FilledIconButton(
                    onClick = onSendMessage,
                    modifier = Modifier.testTag(CHAT_COMPOSER_SEND_TAG),
                    enabled = canSend
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送消息"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationNoticeSection(
    conversation: ConversationUiState,
    invalidConfigText: String
) {
    val notices = buildList {
        if (!conversation.hasValidConfig) {
            add(ConversationNotice(text = invalidConfigText, isError = true))
        }
        if (!conversation.errorText.isNullOrBlank()) {
            add(ConversationNotice(text = conversation.errorText, isError = true))
        }
        if (!conversation.statusText.isNullOrBlank()) {
            add(ConversationNotice(text = conversation.statusText, isError = false))
        }
    }

    if (notices.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(STATUS_SECTION_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        notices.forEach { notice ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (notice.isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Text(
                    text = notice.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notice.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
    }
}

private data class ConversationNotice(
    val text: String,
    val isError: Boolean
)

@Composable
private fun CreateConversationDialog(
    cards: List<CharacterCard>,
    selectedCardId: Long?,
    onDismiss: () -> Unit,
    onSelectCard: (Long) -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                cards.forEach { card ->
                    val selected = selectedCardId == card.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCard(card.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        tonalElevation = if (selected) 1.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ConversationAvatar(
                                label = avatarLabel(card.toSnapshot().effectiveName()),
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = card.toSnapshot().effectiveName(),
                                    style = MaterialTheme.typography.titleMedium
                                )
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
private fun ConversationAvatar(
    label: String,
    modifier: Modifier = Modifier.size(44.dp),
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun EmptyPanel(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

internal fun formatConversationTimestamp(
    updatedAtMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now()
): String {
    if (updatedAtMillis <= 0L) {
        return ""
    }

    val timestamp = Instant.ofEpochMilli(updatedAtMillis).atZone(zoneId)
    val current = now.atZone(zoneId)
    return if (timestamp.toLocalDate() == current.toLocalDate()) {
        SameDayTimeFormatter.format(timestamp)
    } else {
        MonthDayFormatter.format(timestamp)
    }
}

private fun bubbleShape(
    isUser: Boolean,
    isGroupedWithPrevious: Boolean,
    isGroupedWithNext: Boolean
): RoundedCornerShape {
    val rounded = 22.dp
    val joined = 8.dp
    return if (isUser) {
        RoundedCornerShape(
            topStart = rounded,
            topEnd = if (isGroupedWithPrevious) joined else rounded,
            bottomEnd = if (isGroupedWithNext) joined else rounded,
            bottomStart = rounded
        )
    } else {
        RoundedCornerShape(
            topStart = if (isGroupedWithPrevious) joined else rounded,
            topEnd = rounded,
            bottomEnd = rounded,
            bottomStart = if (isGroupedWithNext) joined else rounded
        )
    }
}

private fun avatarLabel(text: String): String {
    return text.trim().takeIf(String::isNotEmpty)?.first()?.toString() ?: "聊"
}

private fun ConversationListItem.summaryText(): String {
    val message = latestMessage ?: return "暂无消息"
    return "${message.role.label()}：${message.content.ifBlank { "暂无消息" }}"
}

private fun ChatRole.label(): String {
    return when (this) {
        ChatRole.User -> "你"
        ChatRole.Assistant -> "角色"
        ChatRole.System -> "系统"
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

@Composable
private fun DevDebugPanel(
    proactiveEnabled: Boolean,
    onGetNextTriggerMs: () -> Long
) {
    var countdownText by remember { mutableStateOf("") }

    if (proactiveEnabled) {
        LaunchedEffect(Unit) {
            while (true) {
                val remaining = onGetNextTriggerMs() - System.currentTimeMillis()
                countdownText = if (remaining > 0) {
                    val totalSeconds = remaining / 1000
                    val mm = totalSeconds / 60
                    val ss = totalSeconds % 60
                    "主动消息: 剩余 %02d:%02d".format(mm, ss)
                } else {
                    "主动消息: 即将触发"
                }
                delay(1_000L)
            }
        }
    }

    if (!proactiveEnabled) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (countdownText.isNotEmpty()) {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
