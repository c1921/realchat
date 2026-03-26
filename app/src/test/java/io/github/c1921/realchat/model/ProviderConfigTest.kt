package io.github.c1921.realchat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigTest {
    @Test
    fun defaultsFor_returnsExpectedPresetValues() {
        assertEquals(
            ProviderConfig(
                providerType = ProviderType.DEEPSEEK,
                model = "deepseek-chat",
                baseUrl = "https://api.deepseek.com"
            ),
            ProviderConfig.defaultsFor(ProviderType.DEEPSEEK)
        )
        assertEquals(
            ProviderConfig(
                providerType = ProviderType.OPENAI,
                model = "",
                baseUrl = "https://api.openai.com/v1"
            ),
            ProviderConfig.defaultsFor(ProviderType.OPENAI)
        )
        assertEquals(
            ProviderConfig(
                providerType = ProviderType.OPENAI_COMPATIBLE,
                model = "",
                baseUrl = ""
            ),
            ProviderConfig.defaultsFor(ProviderType.OPENAI_COMPATIBLE)
        )
    }

    @Test
    fun normalized_trimsTextFieldsAndPreservesProviderType() {
        val normalized = ProviderConfig(
            providerType = ProviderType.OPENAI,
            apiKey = " key ",
            model = " gpt-4o-mini ",
            baseUrl = " https://api.openai.com/v1 "
        ).normalized()

        assertEquals(ProviderType.OPENAI, normalized.providerType)
        assertEquals("key", normalized.apiKey)
        assertEquals("gpt-4o-mini", normalized.model)
        assertEquals("https://api.openai.com/v1", normalized.baseUrl)
    }

    @Test
    fun hasRequiredFields_requiresApiKeyModelAndBaseUrl() {
        assertFalse(
            ProviderConfig.defaultsFor(ProviderType.OPENAI)
                .copy(apiKey = "key")
                .hasRequiredFields()
        )

        assertTrue(
            ProviderConfig.defaultsFor(ProviderType.OPENAI)
                .copy(
                    apiKey = "key",
                    model = "gpt-4o-mini"
                )
                .hasRequiredFields()
        )
    }
}
