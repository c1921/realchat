package io.github.c1921.realchat.data.character

import io.github.c1921.realchat.data.chat.CharacterCardDao
import io.github.c1921.realchat.data.chat.CharacterCardEntity
import io.github.c1921.realchat.model.CharacterCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

data class CharacterCardExportPayload(
    val fileName: String,
    val content: String
)

interface CharacterCardRepository {
    fun observeCards(): Flow<List<CharacterCard>>

    suspend fun getCardById(id: Long): CharacterCard?

    suspend fun saveCard(card: CharacterCard): Long

    suspend fun duplicateCard(id: Long): Long?

    suspend fun deleteCard(id: Long)

    suspend fun importCard(jsonText: String): Long

    suspend fun exportCard(id: Long): CharacterCardExportPayload?

    suspend fun ensureSeedCard(): CharacterCard
}

class RoomCharacterCardRepository(
    private val characterCardDao: CharacterCardDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }
) : CharacterCardRepository {
    override fun observeCards(): Flow<List<CharacterCard>> {
        return characterCardDao.observeAll().map { cards ->
            cards.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getCardById(id: Long): CharacterCard? {
        return characterCardDao.getById(id)?.toDomain()
    }

    override suspend fun saveCard(card: CharacterCard): Long {
        val now = System.currentTimeMillis()
        val normalized = card.normalized()
        val existing = normalized.id.takeIf { it != 0L }?.let { id ->
            characterCardDao.getById(id)
        }
        return if (existing == null) {
            characterCardDao.insert(
                normalized.toEntity(
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            characterCardDao.update(
                normalized.toEntity(
                    createdAt = existing.createdAt,
                    updatedAt = now
                )
            )
            existing.id
        }
    }

    override suspend fun duplicateCard(id: Long): Long? {
        val existing = getCardById(id) ?: return null
        return saveCard(
            existing.copy(
                id = 0L,
                name = "${existing.name.ifBlank { existing.toSnapshot().effectiveName() }} 副本",
                createdAt = 0L,
                updatedAt = 0L
            )
        )
    }

    override suspend fun deleteCard(id: Long) {
        val existing = characterCardDao.getById(id) ?: return
        characterCardDao.delete(existing)
    }

    override suspend fun importCard(jsonText: String): Long {
        val imported = parseCharacterCardJson(jsonText)
        return saveCard(imported)
    }

    override suspend fun exportCard(id: Long): CharacterCardExportPayload? {
        val card = getCardById(id) ?: return null
        val fileName = sanitizeFileName(card.name.ifBlank { card.toSnapshot().effectiveName() }) + ".json"
        return CharacterCardExportPayload(
            fileName = fileName,
            content = buildCharacterCardJson(card, json)
        )
    }

    override suspend fun ensureSeedCard(): CharacterCard {
        if (characterCardDao.count() > 0) {
            return characterCardDao.getMostRecent()?.toDomain()
                ?: CharacterCard(name = "通用助手")
        }

        val seed = CharacterCard(
            name = "通用助手",
            description = "一个稳定、友好、愿意协作的 AI 助手。",
            personality = "冷静、直接、重视事实与上下文连续性。",
            scenario = "你正在与用户进行长期连续的单人对话。",
            firstMes = "你好，我在。我们继续。",
            tags = listOf("default", "assistant")
        )
        val id = saveCard(seed)
        return getCardById(id) ?: seed.copy(id = id)
    }

    private fun CharacterCardEntity.toDomain(): CharacterCard {
        return CharacterCard(
            id = id,
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            firstMes = firstMes,
            mesExample = mesExample,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            alternateGreetings = parseStringList(alternateGreetingsSerialized, json),
            creatorNotes = creatorNotes,
            tags = parseStringList(tagsSerialized, json),
            creator = creator,
            characterVersion = characterVersion,
            rawExtensionsJson = rawExtensionsJson,
            rawUnknownJson = rawUnknownJson,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun CharacterCard.toEntity(
        createdAt: Long,
        updatedAt: Long
    ): CharacterCardEntity {
        return CharacterCardEntity(
            id = id,
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            firstMes = firstMes,
            mesExample = mesExample,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            alternateGreetingsSerialized = encodeStringList(alternateGreetings, json),
            creatorNotes = creatorNotes,
            tagsSerialized = encodeStringList(tags, json),
            creator = creator,
            characterVersion = characterVersion,
            rawExtensionsJson = rawExtensionsJson,
            rawUnknownJson = rawUnknownJson,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "character-card" }
    }
}
