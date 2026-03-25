package io.github.c1921.realchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.c1921.realchat.model.CharacterCard

@Composable
fun CharacterCardsRoute(
    state: CharacterCardsUiState,
    modifier: Modifier = Modifier,
    onOpenCreateCharacterEditor: () -> Unit,
    onOpenEditCharacterEditor: (Long) -> Unit,
    onCancelCharacterEditing: () -> Unit,
    onCharacterEditorChange: (CharacterEditorField, String) -> Unit,
    onSaveCharacterCard: () -> Unit,
    onDuplicateCharacterCard: (Long) -> Unit,
    onDeleteCharacterCard: (Long) -> Unit,
    onImportCharacterCardClick: () -> Unit,
    onRequestCharacterCardExport: (Long) -> Unit
) {
    if (state.isEditing) {
        CharacterEditorScreen(
            state = state,
            modifier = modifier,
            onCancelCharacterEditing = onCancelCharacterEditing,
            onCharacterEditorChange = onCharacterEditorChange,
            onSaveCharacterCard = onSaveCharacterCard
        )
    } else {
        CharacterListScreen(
            state = state,
            modifier = modifier,
            onOpenCreateCharacterEditor = onOpenCreateCharacterEditor,
            onOpenEditCharacterEditor = onOpenEditCharacterEditor,
            onDuplicateCharacterCard = onDuplicateCharacterCard,
            onDeleteCharacterCard = onDeleteCharacterCard,
            onImportCharacterCardClick = onImportCharacterCardClick,
            onRequestCharacterCardExport = onRequestCharacterCardExport
        )
    }
}

@Composable
private fun CharacterListScreen(
    state: CharacterCardsUiState,
    modifier: Modifier = Modifier,
    onOpenCreateCharacterEditor: () -> Unit,
    onOpenEditCharacterEditor: (Long) -> Unit,
    onDuplicateCharacterCard: (Long) -> Unit,
    onDeleteCharacterCard: (Long) -> Unit,
    onImportCharacterCardClick: () -> Unit,
    onRequestCharacterCardExport: (Long) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            title = "角色卡",
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportCharacterCardClick) {
                        Text("导入")
                    }
                    Button(onClick = onOpenCreateCharacterEditor) {
                        Text("新建")
                    }
                }
            }
        )

        if (!state.errorText.isNullOrBlank()) {
            SupportText(
                text = state.errorText,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!state.statusText.isNullOrBlank()) {
            SupportText(
                text = state.statusText,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (state.cards.isEmpty()) {
            Text(
                text = "暂无角色卡。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.cards, key = { it.id }) { card ->
                    CharacterCardItem(
                        card = card,
                        onOpenEdit = { onOpenEditCharacterEditor(card.id) },
                        onDuplicate = { onDuplicateCharacterCard(card.id) },
                        onExport = { onRequestCharacterCardExport(card.id) },
                        onDelete = { onDeleteCharacterCard(card.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterCardItem(
    card: CharacterCard,
    onOpenEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenEdit)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = card.toSnapshot().effectiveName(), style = MaterialTheme.typography.titleMedium)
            if (card.description.isNotBlank()) {
                Text(
                    text = card.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (card.tags.isNotEmpty()) {
                Text(
                    text = card.tags.joinToString(prefix = "#", separator = " #"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenEdit) { Text("编辑") }
                TextButton(onClick = onDuplicate) { Text("复制") }
                TextButton(onClick = onExport) { Text("导出") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun CharacterEditorScreen(
    state: CharacterCardsUiState,
    modifier: Modifier = Modifier,
    onCancelCharacterEditing: () -> Unit,
    onCharacterEditorChange: (CharacterEditorField, String) -> Unit,
    onSaveCharacterCard: () -> Unit
) {
    val editor = state.editor
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            title = if (editor.editingCardId == null) "新建角色卡" else "编辑角色卡",
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancelCharacterEditing) {
                        Text("取消")
                    }
                    Button(onClick = onSaveCharacterCard) {
                        Text("保存")
                    }
                }
            }
        )

        if (!state.errorText.isNullOrBlank()) {
            SupportText(
                text = state.errorText,
                color = MaterialTheme.colorScheme.error
            )
        }

        CharacterEditorFieldItem("角色名", editor.name) {
            onCharacterEditorChange(CharacterEditorField.Name, it)
        }
        CharacterEditorFieldItem("描述", editor.description) {
            onCharacterEditorChange(CharacterEditorField.Description, it)
        }
        CharacterEditorFieldItem("性格", editor.personality) {
            onCharacterEditorChange(CharacterEditorField.Personality, it)
        }
        CharacterEditorFieldItem("场景", editor.scenario) {
            onCharacterEditorChange(CharacterEditorField.Scenario, it)
        }
        CharacterEditorFieldItem("开场白", editor.firstMes, minLines = 3) {
            onCharacterEditorChange(CharacterEditorField.FirstMessage, it)
        }
        CharacterEditorFieldItem("示例对话", editor.mesExample, minLines = 4) {
            onCharacterEditorChange(CharacterEditorField.ExampleMessages, it)
        }
        CharacterEditorFieldItem("System Prompt", editor.systemPrompt, minLines = 4) {
            onCharacterEditorChange(CharacterEditorField.SystemPrompt, it)
        }
        CharacterEditorFieldItem(
            "尾部指令",
            editor.postHistoryInstructions,
            minLines = 3
        ) {
            onCharacterEditorChange(CharacterEditorField.PostHistoryInstructions, it)
        }
        CharacterEditorFieldItem("创作者备注", editor.creatorNotes, minLines = 3) {
            onCharacterEditorChange(CharacterEditorField.CreatorNotes, it)
        }
        CharacterEditorFieldItem("标签，逗号分隔", editor.tagsText) {
            onCharacterEditorChange(CharacterEditorField.Tags, it)
        }
        CharacterEditorFieldItem("作者", editor.creator) {
            onCharacterEditorChange(CharacterEditorField.Creator, it)
        }
        CharacterEditorFieldItem("角色版本", editor.characterVersion) {
            onCharacterEditorChange(CharacterEditorField.CharacterVersion, it)
        }
        CharacterEditorFieldItem("备选开场白，每行一条", editor.alternateGreetingsText, minLines = 4) {
            onCharacterEditorChange(CharacterEditorField.AlternateGreetings, it)
        }
    }
}

@Composable
private fun CharacterEditorFieldItem(
    label: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines
    )
}
