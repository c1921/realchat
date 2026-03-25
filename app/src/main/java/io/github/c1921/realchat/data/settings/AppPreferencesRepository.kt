package io.github.c1921.realchat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.c1921.realchat.model.ProviderConfig
import io.github.c1921.realchat.model.UserPersona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

data class AppPreferences(
    val providerConfig: ProviderConfig = ProviderConfig(),
    val userPersona: UserPersona = UserPersona(),
    val selectedConversationId: Long? = null
)

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun saveProviderConfig(config: ProviderConfig)

    suspend fun saveUserPersona(userPersona: UserPersona)

    suspend fun saveSelectedConversationId(conversationId: Long?)
}

class DataStoreAppPreferencesRepository(
    private val context: Context
) : AppPreferencesRepository {
    override fun observePreferences(): Flow<AppPreferences> {
        return context.dataStore.data.map { preferences ->
            AppPreferences(
                providerConfig = ProviderConfig(
                    apiKey = preferences[API_KEY] ?: "",
                    model = preferences[MODEL] ?: ProviderConfig.DEFAULT_MODEL,
                    baseUrl = preferences[BASE_URL] ?: ProviderConfig.DEFAULT_BASE_URL
                ),
                userPersona = UserPersona(
                    displayName = preferences[PERSONA_NAME] ?: "",
                    description = preferences[PERSONA_DESCRIPTION] ?: ""
                ),
                selectedConversationId = preferences[SELECTED_CONVERSATION_ID]
            )
        }
    }

    override suspend fun saveProviderConfig(config: ProviderConfig) {
        val normalized = config.normalized()
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = normalized.apiKey
            preferences[MODEL] = normalized.model
            preferences[BASE_URL] = normalized.baseUrl
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

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
        val PERSONA_NAME = stringPreferencesKey("persona_name")
        val PERSONA_DESCRIPTION = stringPreferencesKey("persona_description")
        val SELECTED_CONVERSATION_ID = longPreferencesKey("selected_conversation_id")
    }
}
