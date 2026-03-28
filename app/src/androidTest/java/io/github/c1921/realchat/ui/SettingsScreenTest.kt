package io.github.c1921.realchat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.c1921.realchat.model.ProviderType
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun providerSelector_opensDropdownAndUpdatesSelection() {
        composeRule.setContent {
            var providerType by remember { mutableStateOf(ProviderType.DEEPSEEK) }

            MaterialTheme {
                SettingsScreen(
                    settings = SettingsUiState(providerType = providerType),
                    onProviderTypeChange = { providerType = it },
                    onApiKeyChange = { },
                    onModelChange = { },
                    onBaseUrlChange = { },
                    onPersonaNameChange = { },
                    onPersonaDescriptionChange = { },
                    onProactiveEnabledChange = { },
                    onProactiveMinIntervalChange = { },
                    onProactiveMaxIntervalChange = { },
                    onProactiveMaxCountChange = { },
                    onDirectorEnabledChange = { },
                    onDirectorSystemPromptChange = { },
                    onMemoryEnabledChange = { },
                    onMemoryTriggerCountChange = { },
                    onMemoryKeepCountChange = { },
                    onDeveloperModeEnabledChange = { },
                    onSaveSettings = { }
                )
            }
        }

        composeRule.onNodeWithText("AI Provider").assertExists()
        composeRule.onNodeWithText("DeepSeek").assertExists()

        composeRule.onNodeWithTag("provider_selector").performClick()
        composeRule.onNodeWithTag("provider_option_openai").assertExists().performClick()

        composeRule.onNodeWithText("OpenAI").assertExists()
        composeRule.onNodeWithText(
            "OpenAI 官方 Base URL：https://api.openai.com/v1，请填写可用模型名。"
        ).assertExists()
        composeRule.onNodeWithTag("provider_option_openai").assertDoesNotExist()
        composeRule.onNodeWithText("自定义兼容").assertDoesNotExist()
    }
}
