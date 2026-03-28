package io.github.c1921.realchat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.c1921.realchat.model.DirectorSettings
import io.github.c1921.realchat.model.MemorySettings
import io.github.c1921.realchat.model.ProactiveSettings
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.ProviderType
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSummary_opensProviderDetailAndPersistsSelection() {
        composeRule.setContent {
            var settings by remember {
                mutableStateOf(
                    SettingsUiState(
                        providerType = ProviderType.DEEPSEEK,
                        providerConfigs = ProviderConfig.defaultsByProvider()
                    )
                )
            }
            var activeSection by remember { mutableStateOf<SettingsSection?>(null) }

            MaterialTheme {
                SettingsScreen(
                    settings = settings,
                    activeSection = activeSection,
                    onOpenSection = { activeSection = it },
                    onCloseSection = { activeSection = null },
                    onApplyProviderSettings = { selectedProviderType, providerConfigs ->
                        val selectedConfig = providerConfigs.getValue(selectedProviderType)
                        settings = settings.copy(
                            providerType = selectedProviderType,
                            providerConfigs = providerConfigs,
                            apiKey = selectedConfig.apiKey,
                            model = selectedConfig.model,
                            baseUrl = selectedConfig.baseUrl
                        )
                        null
                    },
                    onApplyPersonaSettings = { name, description ->
                        settings = settings.copy(
                            personaName = name,
                            personaDescription = description
                        )
                        null
                    },
                    onApplyProactiveSettings = { proactive ->
                        settings = settings.copy(
                            proactiveEnabled = proactive.enabled,
                            proactiveMinIntervalMinutes = proactive.minIntervalMinutes,
                            proactiveMaxIntervalMinutes = proactive.maxIntervalMinutes,
                            proactiveMaxCount = proactive.maxCount
                        )
                        null
                    },
                    onApplyDirectorSettings = { director: DirectorSettings ->
                        settings = settings.copy(
                            directorEnabled = director.enabled,
                            directorSystemPrompt = director.systemPrompt
                        )
                        null
                    },
                    onApplyMemorySettings = { memory: MemorySettings ->
                        settings = settings.copy(
                            memoryEnabled = memory.enabled,
                            memoryTriggerCount = memory.triggerCount,
                            memoryKeepCount = memory.keepRecentCount
                        )
                        null
                    },
                    onApplyDeveloperModeEnabled = { enabled ->
                        settings = settings.copy(developerModeEnabled = enabled)
                        null
                    }
                )
            }
        }

        composeRule.onNodeWithTag(PROVIDER_SECTION_TAG).assertExists()
        composeRule.onNodeWithText("保存设置").assertDoesNotExist()

        composeRule.onNodeWithTag(PROVIDER_SECTION_TAG).performClick()
        composeRule.onNodeWithTag(PROVIDER_SELECTOR_TAG).performClick()
        composeRule.onNodeWithTag("provider_option_openai").assertExists().performClick()
        composeRule.onNodeWithText("返回").performClick()

        composeRule.onNodeWithTag(PROVIDER_SECTION_TAG).assertTextContains("OpenAI")
    }

    @Test
    fun proactiveIntervalField_allowsClearAndReentryWithoutRollback() {
        composeRule.setContent {
            var settings by remember {
                mutableStateOf(
                    SettingsUiState(
                        proactiveEnabled = true,
                        proactiveMinIntervalMinutes = 30,
                        proactiveMaxIntervalMinutes = 1440,
                        proactiveMaxCount = 5,
                        providerConfigs = ProviderConfig.defaultsByProvider()
                    )
                )
            }
            var activeSection by remember { mutableStateOf<SettingsSection?>(null) }

            MaterialTheme {
                SettingsScreen(
                    settings = settings,
                    activeSection = activeSection,
                    onOpenSection = { activeSection = it },
                    onCloseSection = { activeSection = null },
                    onApplyProviderSettings = { _, _ -> null },
                    onApplyPersonaSettings = { _, _ -> null },
                    onApplyProactiveSettings = { proactive: ProactiveSettings ->
                        settings = settings.copy(
                            proactiveEnabled = proactive.enabled,
                            proactiveMinIntervalMinutes = proactive.minIntervalMinutes,
                            proactiveMaxIntervalMinutes = proactive.maxIntervalMinutes,
                            proactiveMaxCount = proactive.maxCount
                        )
                        null
                    },
                    onApplyDirectorSettings = { _: DirectorSettings -> null },
                    onApplyMemorySettings = { _: MemorySettings -> null },
                    onApplyDeveloperModeEnabled = { _ -> null }
                )
            }
        }

        composeRule.onNodeWithTag(PROACTIVE_SECTION_TAG).performClick()
        composeRule.onNodeWithTag(PROACTIVE_MIN_TAG).performTextClearance()
        composeRule.onNodeWithTag(PROACTIVE_MIN_TAG).assertTextEquals("")

        composeRule.onNodeWithTag(PROACTIVE_MIN_TAG).performTextInput("45")
        composeRule.onNodeWithText("返回").performClick()

        composeRule.onNodeWithTag(PROACTIVE_SECTION_TAG)
            .assertTextContains("45-1440 分钟")
    }
}
