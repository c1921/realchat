package io.github.c1921.realchat.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.c1921.realchat.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "provider_settings")

interface SettingsRepository {
    fun observeConfig(): Flow<ProviderConfig>

    suspend fun saveConfig(config: ProviderConfig)
}

class DataStoreSettingsRepository(
    private val context: Context
) : SettingsRepository {
    override fun observeConfig(): Flow<ProviderConfig> {
        return context.dataStore.data.map { preferences ->
            ProviderConfig(
                apiKey = preferences[API_KEY] ?: "",
                model = preferences[MODEL] ?: ProviderConfig.DEFAULT_MODEL,
                baseUrl = preferences[BASE_URL] ?: ProviderConfig.DEFAULT_BASE_URL
            )
        }
    }

    override suspend fun saveConfig(config: ProviderConfig) {
        val normalized = config.normalized()
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = normalized.apiKey
            preferences[MODEL] = normalized.model
            preferences[BASE_URL] = normalized.baseUrl
        }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val BASE_URL = stringPreferencesKey("base_url")
    }
}
