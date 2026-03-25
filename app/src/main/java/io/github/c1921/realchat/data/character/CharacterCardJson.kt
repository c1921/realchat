package io.github.c1921.realchat.data.character

import io.github.c1921.realchat.model.CharacterCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val KNOWN_V2_DATA_KEYS = setOf(
    "name",
    "description",
    "personality",
    "scenario",
    "first_mes",
    "mes_example",
    "creator_notes",
    "system_prompt",
    "post_history_instructions",
    "alternate_greetings",
    "tags",
    "creator",
    "character_version",
    "extensions"
)

internal fun parseCharacterCardJson(
    text: String,
    json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
): CharacterCard {
    val root = runCatching {
        json.parseToJsonElement(text).jsonObject
    }.getOrElse {
        throw IllegalArgumentException("角色卡 JSON 格式无效。")
    }

    return if (root["spec"]?.jsonPrimitive?.contentOrNull == "chara_card_v2") {
        parseV2CharacterCard(root, json)
    } else {
        parseV1CharacterCard(root)
    }
}

internal fun buildCharacterCardJson(
    card: CharacterCard,
    json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }
): String {
    val normalized = card.normalized()
    val rawUnknownObject = parseJsonObject(normalized.rawUnknownJson, json)
    val extensionsObject = parseJsonObject(normalized.rawExtensionsJson, json)

    val root = buildJsonObject {
        put("spec", JsonPrimitive("chara_card_v2"))
        put("spec_version", JsonPrimitive("2.0"))
        put(
            "data",
            buildJsonObject {
                rawUnknownObject.forEach { (key, value) ->
                    if (key !in KNOWN_V2_DATA_KEYS) {
                        put(key, value)
                    }
                }
                put("name", JsonPrimitive(normalized.name))
                put("description", JsonPrimitive(normalized.description))
                put("personality", JsonPrimitive(normalized.personality))
                put("scenario", JsonPrimitive(normalized.scenario))
                put("first_mes", JsonPrimitive(normalized.firstMes))
                put("mes_example", JsonPrimitive(normalized.mesExample))
                put("creator_notes", JsonPrimitive(normalized.creatorNotes))
                put("system_prompt", JsonPrimitive(normalized.systemPrompt))
                put(
                    "post_history_instructions",
                    JsonPrimitive(normalized.postHistoryInstructions)
                )
                put(
                    "alternate_greetings",
                    buildJsonArray {
                        normalized.alternateGreetings.forEach { greeting ->
                            add(JsonPrimitive(greeting))
                        }
                    }
                )
                put(
                    "tags",
                    buildJsonArray {
                        normalized.tags.forEach { tag ->
                            add(JsonPrimitive(tag))
                        }
                    }
                )
                put("creator", JsonPrimitive(normalized.creator))
                put("character_version", JsonPrimitive(normalized.characterVersion))
                put("extensions", extensionsObject)
            }
        )
    }

    return json.encodeToString(JsonObject.serializer(), root)
}

internal fun encodeStringList(values: List<String>, json: Json): String {
    val array = buildJsonArray {
        values.map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { value ->
                add(JsonPrimitive(value))
            }
    }
    return json.encodeToString(JsonArray.serializer(), array)
}

internal fun parseStringList(serialized: String, json: Json): List<String> {
    return runCatching {
        json.parseToJsonElement(serialized).jsonArray.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        }
    }.getOrDefault(emptyList())
}

private fun parseV1CharacterCard(root: JsonObject): CharacterCard {
    return CharacterCard(
        name = root.stringValue("name"),
        description = root.stringValue("description"),
        personality = root.stringValue("personality"),
        scenario = root.stringValue("scenario"),
        firstMes = root.stringValue("first_mes"),
        mesExample = root.stringValue("mes_example")
    ).normalized()
}

private fun parseV2CharacterCard(
    root: JsonObject,
    json: Json
): CharacterCard {
    val data = root["data"]?.jsonObject
        ?: throw IllegalArgumentException("角色卡缺少 data 字段。")

    val unknownData = buildJsonObject {
        data.forEach { (key, value) ->
            if (key !in KNOWN_V2_DATA_KEYS) {
                put(key, value)
            }
        }
    }

    return CharacterCard(
        name = data.stringValue("name"),
        description = data.stringValue("description"),
        personality = data.stringValue("personality"),
        scenario = data.stringValue("scenario"),
        firstMes = data.stringValue("first_mes"),
        mesExample = data.stringValue("mes_example"),
        creatorNotes = data.stringValue("creator_notes"),
        systemPrompt = data.stringValue("system_prompt"),
        postHistoryInstructions = data.stringValue("post_history_instructions"),
        alternateGreetings = data.stringArray("alternate_greetings"),
        tags = data.stringArray("tags"),
        creator = data.stringValue("creator"),
        characterVersion = data.stringValue("character_version"),
        rawExtensionsJson = data["extensions"]?.toString() ?: "{}",
        rawUnknownJson = json.encodeToString(JsonObject.serializer(), unknownData)
    ).normalized()
}

private fun parseJsonObject(
    serialized: String,
    json: Json
): JsonObject {
    return runCatching {
        json.parseToJsonElement(serialized).jsonObject
    }.getOrDefault(emptyMap<String, JsonElement>().let(::JsonObject))
}

private fun JsonObject.stringValue(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
}

private fun JsonObject.stringArray(key: String): List<String> {
    return this[key]
        ?.jsonArray
        ?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        }
        .orEmpty()
}
