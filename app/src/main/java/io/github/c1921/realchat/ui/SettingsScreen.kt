package io.github.c1921.realchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@androidx.compose.runtime.Composable
fun SettingsScreen(
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onPersonaNameChange: (String) -> Unit,
    onPersonaDescriptionChange: (String) -> Unit,
    onSaveSettings: () -> Unit
) {
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
