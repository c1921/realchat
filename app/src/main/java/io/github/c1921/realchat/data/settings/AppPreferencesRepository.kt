package io.github.c1921.realchat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.c1921.realchat.model.AgentSettings
import io.github.c1921.realchat.model.DirectorSettings
import io.github.c1921.realchat.model.MemorySettings
import io.github.c1921.realchat.model.ProactiveSettings
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.ProviderType
import io.github.c1921.realchat.model.UserPersona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

data class AppPreferences(
    val selectedProviderType: ProviderType = ProviderConfig.DEFAULT_PROVIDER_TYPE,
    val providerConfigs: Map<ProviderType, ProviderConfig> = ProviderConfig.defaultsByProvider(),
    val userPersona: UserPersona = UserPersona(),
    val selectedConversationId: Long? = null,
    val agentSettings: AgentSettings = AgentSettings(),
    val developerModeEnabled: Boolean = false
) {
    val providerConfig: ProviderConfig
        get() = providerConfigs[selectedProviderType]
            ?: ProviderConfig.defaultsFor(selectedProviderType)
}

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun saveProviderSettings(
        selectedProviderType: ProviderType,
        providerConfigs: Map<ProviderType, ProviderConfig>
    )

    suspend fun saveUserPersona(userPersona: UserPersona)

    suspend fun saveSelectedConversationId(conversationId: Long?)

    suspend fun saveAgentSettings(agentSettings: AgentSettings)

    suspend fun saveDeveloperMode(enabled: Boolean)
}

class DataStoreAppPreferencesRepository(
    private val context: Context
) : AppPreferencesRepository {
    override fun observePreferences(): Flow<AppPreferences> {
        return context.dataStore.data.map { preferences ->
            val selectedProviderType = preferences[PROVIDER_TYPE]
                ?.let(::parseProviderType)
                ?: ProviderConfig.DEFAULT_PROVIDER_TYPE

            AppPreferences(
                selectedProviderType = selectedProviderType,
                providerConfigs = ProviderType.entries.associateWith { providerType ->
                    buildProviderConfig(
                        preferences = preferences,
                        providerType = providerType,
                        selectedProviderType = selectedProviderType
                    )
                },
                userPersona = UserPersona(
                    displayName = preferences[PERSONA_NAME] ?: "",
                    description = preferences[PERSONA_DESCRIPTION] ?: ""
                ),
                selectedConversationId = preferences[SELECTED_CONVERSATION_ID],
                agentSettings = buildAgentSettings(preferences),
                developerModeEnabled = preferences[DEVELOPER_MODE_ENABLED] ?: false
            )
        }
    }

    override suspend fun saveProviderSettings(
        selectedProviderType: ProviderType,
        providerConfigs: Map<ProviderType, ProviderConfig>
    ) {
        val normalizedConfigs = ProviderType.entries.associateWith { providerType ->
            (providerConfigs[providerType] ?: ProviderConfig.defaultsFor(providerType))
                .copy(providerType = providerType)
                .normalized()
        }

        context.dataStore.edit { preferences ->
            preferences[PROVIDER_TYPE] = selectedProviderType.name
            normalizedConfigs.forEach { (providerType, config) ->
                preferences[providerApiKey(providerType)] = config.apiKey
                preferences[providerModel(providerType)] = config.model
                preferences[providerBaseUrl(providerType)] = config.baseUrl
            }
        }
    }

    override suspend fun saveUserPersona(userPersona: UserPersona) {
        val normalized = userPersona.normalized()
        context.dataStore.edit { preferences ->
            preferences[PERSONA_NAME] = normalized.displayName
            preferences[PERSONA_DESCRIPTION] = normalized.description
        }
    }

    override suspend fun saveSelectedConversationId(conversationId: Long?) {
        context.dataStore.edit { preferences ->
            if (conversationId == null) {
                preferences.remove(SELECTED_CONVERSATION_ID)
            } else {
                preferences[SELECTED_CONVERSATION_ID] = conversationId
            }
        }
    }

    override suspend fun saveAgentSettings(agentSettings: AgentSettings) {
        context.dataStore.edit { preferences ->
            preferences[PROACTIVE_ENABLED] = agentSettings.proactive.enabled
            preferences[PROACTIVE_MIN_INTERVAL] = agentSettings.proactive.minIntervalMinutes
            preferences[PROACTIVE_MAX_INTERVAL] = agentSettings.proactive.maxIntervalMinutes
            preferences[PROACTIVE_MAX_COUNT] = agentSettings.proactive.maxCount
            preferences[DIRECTOR_ENABLED] = agentSettings.director.enabled
            preferences[DIRECTOR_SYSTEM_PROMPT] = agentSettings.director.systemPrompt
            preferences[MEMORY_ENABLED] = agentSettings.memory.enabled
            preferences[MEMORY_TRIGGER_COUNT] = agentSettings.memory.triggerCount
            preferences[MEMORY_KEEP_COUNT] = agentSettings.memory.keepRecentCount
        }
    }

    override suspend fun saveDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    private companion object {
        val PROVIDER_TYPE = stringPreferencesKey("provider_type")
        val LEGACY_API_KEY = stringPreferencesKey("api_key")
        val LEGACY_MODEL = stringPreferencesKey("model")
        val LEGACY_BASE_URL = stringPreferencesKey("base_url")
        val PERSONA_NAME = stringPreferencesKey("persona_name")
        val PERSONA_DESCRIPTION = stringPreferencesKey("persona_description")
        val SELECTED_CONVERSATION_ID = longPreferencesKey("selected_conversation_id")

        val PROACTIVE_ENABLED = booleanPreferencesKey("proactive_enabled")
        val PROACTIVE_MIN_INTERVAL = intPreferencesKey("proactive_min_interval_minutes")
        val PROACTIVE_MAX_INTERVAL = intPreferencesKey("proactive_max_interval_minutes")
        val PROACTIVE_MAX_COUNT = intPreferencesKey("proactive_max_count")
        val DIRECTOR_ENABLED = booleanPreferencesKey("director_enabled")
        val DIRECTOR_SYSTEM_PROMPT = stringPreferencesKey("director_system_prompt")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val MEMORY_TRIGGER_COUNT = intPreferencesKey("memory_trigger_count")
        val MEMORY_KEEP_COUNT = intPreferencesKey("memory_keep_count")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")

        fun parseProviderType(value: String): ProviderType {
            return runCatching { ProviderType.valueOf(value) }
                .getOrDefault(ProviderConfig.DEFAULT_PROVIDER_TYPE)
        }

        fun buildProviderConfig(
            preferences: Preferences,
            providerType: ProviderType,
            selectedProviderType: ProviderType
        ): ProviderConfig {
            val defaults = ProviderConfig.defaultsFor(providerType)
            val legacyApiKey = if (providerType == selectedProviderType) {
                preferences[LEGACY_API_KEY]
            } else {
                null
            }
            val legacyModel = if (providerType == selectedProviderType) {
                preferences[LEGACY_MODEL]
            } else {
                null
            }
            val legacyBaseUrl = if (providerType == selectedProviderType) {
                preferences[LEGACY_BASE_URL]
            } else {
                null
            }

            return ProviderConfig(
                providerType = providerType,
                apiKey = preferences[providerApiKey(providerType)] ?: legacyApiKey ?: defaults.apiKey,
                model = preferences[providerModel(providerType)] ?: legacyModel ?: defaults.model,
                baseUrl = preferences[providerBaseUrl(providerType)] ?: legacyBaseUrl ?: defaults.baseUrl
            )
        }

        fun providerApiKey(providerType: ProviderType) =
            stringPreferencesKey("api_key_${providerType.name.lowercase()}")

        fun providerModel(providerType: ProviderType) =
            stringPreferencesKey("model_${providerType.name.lowercase()}")

        fun providerBaseUrl(providerType: ProviderType) =
            stringPreferencesKey("base_url_${providerType.name.lowercase()}")

        fun buildAgentSettings(preferences: Preferences): AgentSettings {
            val defaults = AgentSettings()
            return AgentSettings(
                proactive = ProactiveSettings(
                    enabled = preferences[PROACTIVE_ENABLED] ?: defaults.proactive.enabled,
                    minIntervalMinutes = preferences[PROACTIVE_MIN_INTERVAL]
                        ?: defaults.proactive.minIntervalMinutes,
                    maxIntervalMinutes = preferences[PROACTIVE_MAX_INTERVAL]
                        ?: defaults.proactive.maxIntervalMinutes,
                    maxCount = preferences[PROACTIVE_MAX_COUNT] ?: defaults.proactive.maxCount
                ),
                director = DirectorSettings(
                    enabled = preferences[DIRECTOR_ENABLED] ?: defaults.director.enabled,
                    systemPrompt = preferences[DIRECTOR_SYSTEM_PROMPT]
                        ?: defaults.director.systemPrompt
                ),
                memory = MemorySettings(
                    enabled = preferences[MEMORY_ENABLED] ?: defaults.memory.enabled,
                    triggerCount = preferences[MEMORY_TRIGGER_COUNT]
                        ?: defaults.memory.triggerCount,
                    keepRecentCount = preferences[MEMORY_KEEP_COUNT]
                        ?: defaults.memory.keepRecentCount
                )
            )
        }
    }
}
