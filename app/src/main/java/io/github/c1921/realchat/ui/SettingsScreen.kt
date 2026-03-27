package io.github.c1921.realchat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.c1921.realchat.model.ProviderType

@androidx.compose.runtime.Composable
fun SettingsScreen(
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
    onProviderTypeChange: (ProviderType) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onPersonaNameChange: (String) -> Unit,
    onPersonaDescriptionChange: (String) -> Unit,
    onProactiveEnabledChange: (Boolean) -> Unit,
    onProactiveMinIntervalChange: (String) -> Unit,
    onProactiveMaxIntervalChange: (String) -> Unit,
    onDirectorEnabledChange: (Boolean) -> Unit,
    onDirectorSystemPromptChange: (String) -> Unit,
    onMemoryEnabledChange: (Boolean) -> Unit,
    onMemoryTriggerCountChange: (String) -> Unit,
    onMemoryKeepCountChange: (String) -> Unit,
    onDeveloperModeEnabledChange: (Boolean) -> Unit,
    onSaveSettings: () -> Unit
) {
    var providerMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            title = "设置",
            actions = { }
        )

        Text(
            text = "AI Provider",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { providerMenuExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PROVIDER_SELECTOR_TAG)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = settings.providerType.label(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (providerMenuExpanded) "▲" else "▼",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = providerMenuExpanded,
                onDismissRequest = { providerMenuExpanded = false }
            ) {
                ProviderType.entries.forEach { providerType ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag(providerType.optionTag()),
                        text = { Text(providerType.label()) },
                        onClick = {
                            onProviderTypeChange(providerType)
                            providerMenuExpanded = false
                        }
                    )
                }
            }
        }

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

        OutlinedTextField(
            value = settings.personaName,
            onValueChange = onPersonaNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("用户 Persona 名称") },
            singleLine = true
        )

        OutlinedTextField(
            value = settings.personaDescription,
            onValueChange = onPersonaDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("用户 Persona 描述") },
            minLines = 4
        )

        SupportText(
            text = settings.providerType.supportText(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Divider()

        Text(
            text = "Agent 设置",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("主动消息", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = settings.proactiveEnabled,
                onCheckedChange = onProactiveEnabledChange
            )
        }

        AnimatedVisibility(visible = settings.proactiveEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settings.proactiveMinIntervalMinutes.toString(),
                    onValueChange = onProactiveMinIntervalChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最短间隔（分钟）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = settings.proactiveMaxIntervalMinutes.toString(),
                    onValueChange = onProactiveMaxIntervalChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最长间隔（分钟）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("导演系统", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = settings.directorEnabled,
                onCheckedChange = onDirectorEnabledChange
            )
        }

        AnimatedVisibility(visible = settings.directorEnabled) {
            OutlinedTextField(
                value = settings.directorSystemPrompt,
                onValueChange = onDirectorSystemPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("导演提示词（留空使用默认）") },
                minLines = 3
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("记忆摘要", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = settings.memoryEnabled,
                onCheckedChange = onMemoryEnabledChange
            )
        }

        AnimatedVisibility(visible = settings.memoryEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settings.memoryTriggerCount.toString(),
                    onValueChange = onMemoryTriggerCountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("触发压缩的消息数阈值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = settings.memoryKeepCount.toString(),
                    onValueChange = onMemoryKeepCountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("保留最近消息数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        Divider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("开发者模式", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = settings.developerModeEnabled,
                onCheckedChange = onDeveloperModeEnabledChange
            )
        }

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

private fun ProviderType.label(): String {
    return when (this) {
        ProviderType.DEEPSEEK -> "DeepSeek"
        ProviderType.OPENAI -> "OpenAI"
        ProviderType.OPENAI_COMPATIBLE -> "自定义兼容"
    }
}

private fun ProviderType.supportText(): String {
    return when (this) {
        ProviderType.DEEPSEEK -> "DeepSeek 默认 Base URL：https://api.deepseek.com"
        ProviderType.OPENAI -> "OpenAI 官方 Base URL：https://api.openai.com/v1，请填写可用模型名。"
        ProviderType.OPENAI_COMPATIBLE -> "请填写兼容 OpenAI Chat Completions 的 Base URL 和模型。"
    }
}

private const val PROVIDER_SELECTOR_TAG = "provider_selector"

private fun ProviderType.optionTag(): String {
    return "provider_option_${name.lowercase()}"
}
