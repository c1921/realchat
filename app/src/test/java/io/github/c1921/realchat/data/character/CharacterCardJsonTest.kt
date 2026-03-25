package io.github.c1921.realchat.data.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardJsonTest {
    @Test
    fun parseCharacterCardJson_supportsV1Card() {
        val card = parseCharacterCardJson(
            """
            {
              "name": "Alice",
              "description": "勇敢的侦探",
              "personality": "敏锐",
              "scenario": "雨夜调查",
              "first_mes": "我到了现场。",
              "mes_example": "{{char}}：继续查。"
            }
            """.trimIndent()
        )

        assertEquals("Alice", card.name)
        assertEquals("勇敢的侦探", card.description)
        assertEquals("敏锐", card.personality)
        assertEquals("雨夜调查", card.scenario)
        assertEquals("我到了现场。", card.firstMes)
        assertEquals("{{char}}：继续查。", card.mesExample)
    }

    @Test
    fun buildCharacterCardJson_roundTripsUnknownV2Fields() {
        val imported = parseCharacterCardJson(
            """
            {
              "spec": "chara_card_v2",
              "spec_version": "2.0",
              "data": {
                "name": "Eve",
                "description": "黑客",
                "personality": "克制",
                "scenario": "赛博城市",
                "first_mes": "我在线。",
                "mes_example": "",
                "system_prompt": "保持 {{char}} 的口吻。{{original}}",
                "post_history_instructions": "只回复一句。",
                "alternate_greetings": ["第二个开场"],
                "tags": ["cyber", "noir"],
                "creator": "tester",
                "character_version": "1.1",
                "extensions": {"x-test": {"enabled": true}},
                "character_book": {"entries": []}
              }
            }
            """.trimIndent()
        )

        val exported = buildCharacterCardJson(imported)

        assertTrue(exported.contains("\"character_book\""))
        assertTrue(exported.contains("\"x-test\""))
        assertTrue(exported.contains("\"alternate_greetings\""))
    }
}
