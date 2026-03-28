package io.github.c1921.realchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.c1921.realchat.data.chat.buildChatCompletionsUrl
import io.github.c1921.realchat.model.DirectorSettings
import io.github.c1921.realchat.model.MemorySettings
import io.github.c1921.realchat.model.ProactiveSettings
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.ProviderType
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun SettingsScreen(
    settings: SettingsUiState,
    activeSection: SettingsSection?,
    modifier: Modifier = Modifier,
    onOpenSection: (SettingsSection) -> Unit,
    onCloseSection: () -> Unit,
    onApplyProviderSettings: suspend (ProviderType, Map<ProviderType, ProviderConfig>) -> String?,
    onApplyPersonaSettings: suspend (String, String) -> String?,
    onApplyProactiveSettings: suspend (ProactiveSettings) -> String?,
    onApplyDirectorSettings: suspend (DirectorSettings) -> String?,
    onApplyMemorySettings: suspend (MemorySettings) -> String?,
    onApplyDeveloperModeEnabled: suspend (Boolean) -> String?
) {
    when (activeSection) {
        null -> SettingsHomeScreen(
            settings = settings,
            modifier = modifier,
            onOpenSection = onOpenSection,
            onApplyDeveloperModeEnabled = onApplyDeveloperModeEnabled
        )

        SettingsSection.Provider -> ProviderSettingsDetailScreen(
            settings = settings,
            modifier = modifier,
            onCloseSection = onCloseSection,
            onApplyProviderSettings = onApplyProviderSettings
        )

        SettingsSection.Persona -> PersonaSettingsDetailScreen(
            settings = settings,
            modifier = modifier,
            onCloseSection = onCloseSection,
            onApplyPersonaSettings = onApplyPersonaSettings
        )

        SettingsSection.Proactive -> ProactiveSettingsDetailScreen(
            settings = settings,
            modifier = modifier,
            onCloseSection = onCloseSection,
            onApplyProactiveSettings = onApplyProactiveSettings
        )

        SettingsSection.Director -> DirectorSettingsDetailScreen(
            settings = settings,
            modifier = modifier,
            onCloseSection = onCloseSection,
            onApplyDirectorSettings = onApplyDirectorSettings
        )

        SettingsSection.Memory -> MemorySettingsDetailScreen(
            settings = settings,
            modifier = modifier,
            onCloseSection = onCloseSection,
            onApplyMemorySettings = onApplyMemorySettings
        )
    }
}

@Composable
private fun SettingsHomeScreen(
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
    onOpenSection: (SettingsSection) -> Unit,
    onApplyDeveloperModeEnabled: suspend (Boolean) -> String?
) {
    val scope = rememberCoroutineScope()

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

        if (!settings.errorText.isNullOrBlank()) {
            SupportText(
                text = settings.errorText,
                color = MaterialTheme.colorScheme.error
            )
        }

        SettingsSummaryItem(
            title = "AI Provider",
            summary = providerSummary(settings),
            tag = PROVIDER_SECTION_TAG,
            onClick = { onOpenSection(SettingsSection.Provider) }
        )
        SettingsSummaryItem(
            title = "用户 Persona",
            summary = personaSummary(settings),
            tag = PERSONA_SECTION_TAG,
            onClick = { onOpenSection(SettingsSection.Persona) }
        )
        SettingsSummaryItem(
            title = "主动消息",
            summary = proactiveSummary(settings),
            tag = PROACTIVE_SECTION_TAG,
            onClick = { onOpenSection(SettingsSection.Proactive) }
        )
        SettingsSummaryItem(
            title = "导演系统",
            summary = directorSummary(settings),
            tag = DIRECTOR_SECTION_TAG,
            onClick = { onOpenSection(SettingsSection.Director) }
        )
        SettingsSummaryItem(
            title = "记忆摘要",
            summary = memorySummary(settings),
            tag = MEMORY_SECTION_TAG,
            onClick = { onOpenSection(SettingsSection.Memory) }
        )

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("开发者模式", style = MaterialTheme.typography.bodyLarge)
                SupportText(
                    text = "显示主动消息倒计时和发送计数。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.developerModeEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        onApplyDeveloperModeEnabled(enabled)
                    }
                }
            )
        }
    }
}

@Composable
private fun ProviderSettingsDetailScreen(
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
    onCloseSection: () -> Unit,
    onApplyProviderSettings: suspend (ProviderType, Map<ProviderType, ProviderConfig>) -> String?
) {
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var selectedProviderName by rememberSaveable {
        mutableStateOf(settings.providerType.name)
    }
    var providerDrafts by rememberSaveable(stateSaver = ProviderDraftsSaver) {
        mutableStateOf(settings.providerConfigs.toProviderDrafts())
    }
    var baseUrlError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selectedProviderType = ProviderType.valueOf(selectedProviderName)
    val selectedDraft = providerDrafts[selectedProviderType]
        ?: ProviderConfigDraft.from(settings.providerConfigs[selectedProviderType])

    suspend fun applySettings(): String? {
        val providerType = ProviderType.valueOf(selectedProviderName)
        val draft = providerDrafts[providerType]
            ?: ProviderConfigDraft.from(settings.providerConfigs[providerType])
        val selectedConfig = ProviderConfig(
            providerType = providerType,
            apiKey = draft.apiKey,
            model = draft.model,
            baseUrl = draft.baseUrl
        ).normalized()

        if (selectedConfig.baseUrl.isNotEmpty()) {
            val endpoint = buildChatCompletionsUrl(selectedConfig.baseUrl)
            if (endpoint.toHttpUrlOrNull() == null) {
                baseUrlError = "Base URL 格式不正确。"
                return baseUrlError
            }
        }

        baseUrlError = null
        val error = onApplyProviderSettings(providerType, providerDrafts.toProviderConfigs())
        if (error == "Base URL 格式不正确。") {
            baseUrlError = error
        }
        return error
    }

    fun updateSelectedDraft(transform: (ProviderConfigDraft) -> ProviderConfigDraft) {
        val providerType = ProviderType.valueOf(selectedProviderName)
        val currentDraft = providerDrafts[providerType]
            ?: ProviderConfigDraft.from(settings.providerConfigs[providerType])
        providerDrafts = providerDrafts.toMutableMap().apply {
            put(providerType, transform(currentDraft))
        }
    }

    fun launchApply(closeOnSuccess: Boolean = false) {
        scope.launch {
            val error = applySettings()
            if (error == null && closeOnSuccess) {
                onCloseSection()
            }
        }
    }

    BackHandler {
        launchApply(closeOnSuccess = true)
    }

    SettingsDetailLayout(
        title = "AI Provider",
        errorText = settings.errorText,
        modifier = modifier,
        onBack = { launchApply(closeOnSuccess = true) }
    ) {
        Text(
            text = "当前 Provider",
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
                        text = selectedProviderType.label(),
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
                            selectedProviderName = providerType.name
                            providerMenuExpanded = false
                            baseUrlError = null
                            launchApply()
                        }
                    )
                }
            }
        }

        SettingsAutoApplyTextField(
            value = selectedDraft.apiKey,
            onValueChange = { updateSelectedDraft { current -> current.copy(apiKey = it) } },
            label = "API Key",
            modifier = Modifier.testTag(PROVIDER_API_KEY_TAG),
            singleLine = true,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            onCommit = { launchApply() }
        )

        SettingsAutoApplyTextField(
            value = selectedDraft.model,
            onValueChange = { updateSelectedDraft { current -> current.copy(model = it) } },
            label = "模型",
            modifier = Modifier.testTag(PROVIDER_MODEL_TAG),
            singleLine = true,
            onCommit = { launchApply() }
        )

        SettingsAutoApplyTextField(
            value = selectedDraft.baseUrl,
            onValueChange = {
                baseUrlError = null
                updateSelectedDraft { current -> current.copy(baseUrl = it) }
            },
            label = "Base URL",
            modifier = Modifier.testTag(PROVIDER_BASE_URL_TAG),
            singleLine = true,
            keyboardType = KeyboardType.Uri,
            errorText = baseUrlError,
            onCommit = { launchApply() }
        )

        SupportText(
            text = selectedProviderType.supportText(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PersonaSettingsDetailScreen(
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
    onCloseSection: () -> Unit,
    onApplyPersonaSettings: suspend (String, String) -> String?
) {
    var personaName by rememberSaveable { mutableStateOf(settings.personaName) }
    var personaDescription by rememberSaveable { mutableStateOf(settings.personaDescription) }
    val scope = rememberCoroutineScope()

    fun launchApply(closeOnSuccess: Boolean = false) {
        scope.launch {
            val error = onApplyPersonaSettings(personaName, personaDescription)
            if (error == null && closeOnSuccess) {
                onCloseSection()
            }
        }
    }

    BackHandler {
        launchApply(closeOnSuccess = true)
    }

    SettingsDetailLayout(
        title = "用户 Persona",
        errorText = settings.errorText,
        modifier = modifier,
        onBack = { launchApply(closeOnSuccess = true) }
    ) {
        SettingsAutoApplyTextField(
            value = personaName,
            onValueChange = { personaName = it },
            label = "用户 Persona 名称",
            modifier = Modifier.testTag(PERSONA_NAME_TAG),
            singleLine = true,
            onCommit = { launchApply() }
        )

        SettingsAutoApplyTextField(
            value = personaDescription,
            onValueChange = { personaDescription = it },
            label = "用户 Persona 描述",
            modifier = Modifier.testTag(PERSONA_DESCRIPTION_TAG),
            minLines = 4,
            onCommit = { launchApply() }
        )
    }
}

@Composable
private fun ProactiveSettingsDetailScreen(
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
    onCloseSection: () -> Unit,
    onApplyProactiveSettings: suspend (ProactiveSettings) -> String?
) {
    var enabled by rememberSaveable { mutableStateOf(settings.proactiveEnabled) }
    var minIntervalText by rememberSaveable { mutableStateOf(settings.proactiveMinIntervalMinutes.toString()) }
    var maxIntervalText by rememberSaveable { mutableStateOf(settings.proactiveMaxIntervalMinutes.toString()) }
    var maxCountText by rememberSaveable { mutableStateOf(settings.proactiveMaxCount.toString()) }
    var minError by rememberSaveable { mutableStateOf<String?>(null) }
    var maxError by rememberSaveable { mutableStateOf<String?>(null) }
    var countError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun buildSettings(): ProactiveSettings? {
        if (!enabled) {
            return ProactiveSettings(
                enabled = false,
                minIntervalMinutes = minIntervalText.toIntOrNull()
                    ?: settings.proactiveMinIntervalMinutes,
                maxIntervalMinutes = maxIntervalText.toIntOrNull()
                    ?: settings.proactiveMaxIntervalMinutes,
                maxCount = maxCountText.toIntOrNull()
                    ?: settings.proactiveMaxCount
            )
        }

        val minInterval = minIntervalText.toIntOrNull()
        val maxInterval = maxIntervalText.toIntOrNull()
        val maxCount = maxCountText.toIntOrNull()

        minError = when {
            minInterval == null -> "请输入数字。"
            minInterval < 3 -> "最短间隔不能小于 3 分钟。"
            else -> null
        }
        maxError = when {
            maxInterval == null -> "请输入数字。"
            minInterval != null && maxInterval < minInterval -> "最长间隔不能小于最短间隔。"
            else -> null
        }
        countError = when {
            maxCount == null -> "请输入数字。"
            maxCount < 1 -> "最多发送次数不能小于 1。"
            else -> null
        }

        if (minError != null || maxError != null || countError != null) {
            return null
        }

        return ProactiveSettings(
            enabled = true,
            minIntervalMinutes = minInterval!!,
            maxIntervalMinutes = maxInterval!!,
            maxCount = maxCount!!
        )
    }

    fun launchApply(closeOnSuccess: Boolean = false) {
        scope.launch {
            val candidate = buildSettings() ?: return@launch
            val error = onApplyProactiveSettings(candidate)
            if (error == null && closeOnSuccess) {
                onCloseSection()
            }
        }
    }

    BackHandler {
        launchApply(closeOnSuccess = true)
    }

    SettingsDetailLayout(
        title = "主动消息",
        errorText = settings.errorText,
        modifier = modifier,
        onBack = { launchApply(closeOnSuccess = true) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用主动消息", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    if (!checked) {
                        minError = null
                        maxError = null
                        countError = null
                    }
                    launchApply()
                }
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsAutoApplyTextField(
                    value = minIntervalText,
                    onValueChange = {
                        minIntervalText = it
                        minError = null
                    },
                    label = "最短间隔（分钟）",
                    modifier = Modifier.testTag(PROACTIVE_MIN_TAG),
                    singleLine = true,
                    keyboardType = KeyboardType.Number,
                    errorText = minError,
                    onCommit = { launchApply() }
                )

                SettingsAutoApplyTextField(
                    value = maxIntervalText,
                    onValueChange = {
                        maxIntervalText = it
                        maxError = null
                    },
                    label = "最长间隔（分钟）",
                    modifier = Modifier.testTag(PROACTIVE_MAX_TAG),
                    singleLine = true,
                    keyboardType = KeyboardType.Number,
                    errorText = maxError,
                    onCommit = { launchApply() }
                )

                SettingsAutoApplyTextField(
                    value = maxCountText,
                    onValueChange = {
                        maxCountText = it
                        countError = null
                    },
                    label = "最多发送次数（用户回复后重置）",
                    modifier = Modifier.testTag(PROACTIVE_COUNT_TAG),
                    singleLine = true,
                    keyboardType = KeyboardType.Number,
                    errorText = countError,
                    onCommit = { launchApply() }
                )
            }
        }
    }
}

@Composable
private fun DirectorSettingsDetailScreen(
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
    onCloseSection: () -> Unit,
    onApplyDirectorSettings: suspend (DirectorSettings) -> String?
) {
    var enabled by rememberSaveable { mutableStateOf(settings.directorEnabled) }
    var systemPrompt by rememberSaveable { mutableStateOf(settings.directorSystemPrompt) }
    val scope = rememberCoroutineScope()

    fun launchApply(closeOnSuccess: Boolean = false) {
        scope.launch {
            val error = onApplyDirectorSettings(
                DirectorSettings(
                    enabled = enabled,
                    systemPrompt = systemPrompt
                )
            )
            if (error == null && closeOnSuccess) {
                onCloseSection()
            }
        }
    }

    BackHandler {
        launchApply(closeOnSuccess = true)
    }

    SettingsDetailLayout(
        title = "导演系统",
        errorText = settings.errorText,
        modifier = modifier,
        onBack = { launchApply(closeOnSuccess = true) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用导演系统", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    launchApply()
                }
            )
        }

        AnimatedVisibility(visible = enabled) {
            SettingsAutoApplyTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = "导演提示词（留空使用默认）",
                modifier = Modifier.testTag(DIRECTOR_PROMPT_TAG),
                minLines = 4,
                onCommit = { launchApply() }
            )
        }
    }
}

@Composable
private fun MemorySettingsDetailScreen(
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
    onCloseSection: () -> Unit,
    onApplyMemorySettings: suspend (MemorySettings) -> String?
) {
    var enabled by rememberSaveable { mutableStateOf(settings.memoryEnabled) }
    var triggerCountText by rememberSaveable { mutableStateOf(settings.memoryTriggerCount.toString()) }
    var keepCountText by rememberSaveable { mutableStateOf(settings.memoryKeepCount.toString()) }
    var triggerError by rememberSaveable { mutableStateOf<String?>(null) }
    var keepError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun buildSettings(): MemorySettings? {
        if (!enabled) {
            return MemorySettings(
                enabled = false,
                triggerCount = triggerCountText.toIntOrNull() ?: settings.memoryTriggerCount,
                keepRecentCount = keepCountText.toIntOrNull() ?: settings.memoryKeepCount
            )
        }

        val triggerCount = triggerCountText.toIntOrNull()
        val keepCount = keepCountText.toIntOrNull()

        triggerError = when {
            triggerCount == null -> "请输入数字。"
            triggerCount < 1 -> "触发阈值不能小于 1。"
            else -> null
        }
        keepError = when {
            keepCount == null -> "请输入数字。"
            keepCount < 1 -> "保留消息数不能小于 1。"
            else -> null
        }

        if (triggerError != null || keepError != null) {
            return null
        }

        return MemorySettings(
            enabled = true,
            triggerCount = triggerCount!!,
            keepRecentCount = keepCount!!
        )
    }

    fun launchApply(closeOnSuccess: Boolean = false) {
        scope.launch {
            val candidate = buildSettings() ?: return@launch
            val error = onApplyMemorySettings(candidate)
            if (error == null && closeOnSuccess) {
                onCloseSection()
            }
        }
    }

    BackHandler {
        launchApply(closeOnSuccess = true)
    }

    SettingsDetailLayout(
        title = "记忆摘要",
        errorText = settings.errorText,
        modifier = modifier,
        onBack = { launchApply(closeOnSuccess = true) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("启用记忆摘要", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    if (!checked) {
                        triggerError = null
                        keepError = null
                    }
                    launchApply()
                }
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsAutoApplyTextField(
                    value = triggerCountText,
                    onValueChange = {
                        triggerCountText = it
                        triggerError = null
                    },
                    label = "触发压缩的消息数阈值",
                    modifier = Modifier.testTag(MEMORY_TRIGGER_TAG),
                    singleLine = true,
                    keyboardType = KeyboardType.Number,
                    errorText = triggerError,
                    onCommit = { launchApply() }
                )

                SettingsAutoApplyTextField(
                    value = keepCountText,
                    onValueChange = {
                        keepCountText = it
                        keepError = null
                    },
                    label = "保留最近消息数",
                    modifier = Modifier.testTag(MEMORY_KEEP_TAG),
                    singleLine = true,
                    keyboardType = KeyboardType.Number,
                    errorText = keepError,
                    onCommit = { launchApply() }
                )
            }
        }
    }
}

@Composable
private fun SettingsDetailLayout(
    title: String,
    errorText: String?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            title = title,
            actions = {
                OutlinedButton(onClick = onBack) {
                    Text("返回")
                }
            }
        )

        if (!errorText.isNullOrBlank()) {
            SupportText(
                text = errorText,
                color = MaterialTheme.colorScheme.error
            )
        }

        content()
    }
}

@Composable
private fun SettingsSummaryItem(
    title: String,
    summary: String,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            SupportText(
                text = summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsAutoApplyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    errorText: String? = null,
    onCommit: () -> Unit
) {
    var hadFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (hadFocus && !focusState.isFocused) {
                    onCommit()
                }
                hadFocus = focusState.isFocused
            },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = !errorText.isNullOrBlank(),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        ),
        supportingText = if (errorText.isNullOrBlank()) {
            null
        } else {
            { Text(errorText) }
        }
    )
}

private fun SettingsUiState.selectedProviderConfig(): ProviderConfig {
    return providerConfigs[providerType]
        ?.copy(providerType = providerType)
        ?: ProviderConfig.defaultsFor(providerType)
}

private fun providerSummary(settings: SettingsUiState): String {
    val selectedConfig = settings.selectedProviderConfig()
    val parts = buildList {
        add(settings.providerType.label())
        if (selectedConfig.model.isNotBlank()) {
            add(selectedConfig.model)
        }
        if (selectedConfig.baseUrl.isNotBlank()) {
            add(selectedConfig.baseUrl)
        }
    }
    return parts.joinToString(" · ")
}

private fun personaSummary(settings: SettingsUiState): String {
    val name = settings.personaName.ifBlank { "未设置名称" }
    val description = settings.personaDescription.trim()
    return if (description.isBlank()) {
        name
    } else {
        "$name · ${description.take(24)}"
    }
}

private fun proactiveSummary(settings: SettingsUiState): String {
    return if (settings.proactiveEnabled) {
        "已启用 · ${settings.proactiveMinIntervalMinutes}-${settings.proactiveMaxIntervalMinutes} 分钟 · 最多 ${settings.proactiveMaxCount} 次"
    } else {
        "已关闭"
    }
}

private fun directorSummary(settings: SettingsUiState): String {
    return when {
        !settings.directorEnabled -> "已关闭"
        settings.directorSystemPrompt.isBlank() -> "已启用 · 使用默认提示词"
        else -> "已启用 · 自定义提示词"
    }
}

private fun memorySummary(settings: SettingsUiState): String {
    return if (settings.memoryEnabled) {
        "已启用 · 阈值 ${settings.memoryTriggerCount} · 保留 ${settings.memoryKeepCount}"
    } else {
        "已关闭"
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

private fun ProviderType.optionTag(): String {
    return "provider_option_${name.lowercase()}"
}

private fun Map<ProviderType, ProviderConfig>.toProviderDrafts(): Map<ProviderType, ProviderConfigDraft> {
    return ProviderType.entries.associateWith { providerType ->
        ProviderConfigDraft.from(this[providerType])
    }
}

private fun Map<ProviderType, ProviderConfigDraft>.toProviderConfigs(): Map<ProviderType, ProviderConfig> {
    return ProviderType.entries.associateWith { providerType ->
        val draft = this[providerType] ?: ProviderConfigDraft()
        ProviderConfig(
            providerType = providerType,
            apiKey = draft.apiKey,
            model = draft.model,
            baseUrl = draft.baseUrl
        )
    }
}

private data class ProviderConfigDraft(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = ""
) {
    companion object {
        fun from(config: ProviderConfig?): ProviderConfigDraft {
            return ProviderConfigDraft(
                apiKey = config?.apiKey.orEmpty(),
                model = config?.model.orEmpty(),
                baseUrl = config?.baseUrl.orEmpty()
            )
        }
    }
}

private val ProviderDraftsSaver = mapSaver(
    save = { drafts ->
        buildMap {
            ProviderType.entries.forEach { providerType ->
                val draft = drafts[providerType] ?: ProviderConfigDraft()
                put("${providerType.name}_apiKey", draft.apiKey)
                put("${providerType.name}_model", draft.model)
                put("${providerType.name}_baseUrl", draft.baseUrl)
            }
        }
    },
    restore = { restored ->
        ProviderType.entries.associateWith { providerType ->
            ProviderConfigDraft(
                apiKey = restored["${providerType.name}_apiKey"] as? String ?: "",
                model = restored["${providerType.name}_model"] as? String ?: "",
                baseUrl = restored["${providerType.name}_baseUrl"] as? String ?: ""
            )
        }
    }
)

internal const val PROVIDER_SELECTOR_TAG = "provider_selector"
internal const val PROVIDER_SECTION_TAG = "settings_section_provider"
internal const val PERSONA_SECTION_TAG = "settings_section_persona"
internal const val PROACTIVE_SECTION_TAG = "settings_section_proactive"
internal const val DIRECTOR_SECTION_TAG = "settings_section_director"
internal const val MEMORY_SECTION_TAG = "settings_section_memory"
internal const val PROVIDER_API_KEY_TAG = "settings_provider_api_key"
internal const val PROVIDER_MODEL_TAG = "settings_provider_model"
internal const val PROVIDER_BASE_URL_TAG = "settings_provider_base_url"
internal const val PERSONA_NAME_TAG = "settings_persona_name"
internal const val PERSONA_DESCRIPTION_TAG = "settings_persona_description"
internal const val PROACTIVE_MIN_TAG = "settings_proactive_min"
internal const val PROACTIVE_MAX_TAG = "settings_proactive_max"
internal const val PROACTIVE_COUNT_TAG = "settings_proactive_count"
internal const val DIRECTOR_PROMPT_TAG = "settings_director_prompt"
internal const val MEMORY_TRIGGER_TAG = "settings_memory_trigger"
internal const val MEMORY_KEEP_TAG = "settings_memory_keep"
