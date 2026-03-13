from __future__ import annotations

import json
from pathlib import Path
import sqlite3
from typing import Any

from .models import (
    CharacterProfile,
    LoreEntry,
    MemoryCandidate,
    MemoryEpisode,
    MemoryFact,
    MessageRecord,
    SessionState,
    SummarySnapshot,
    UserPersona,
    normalize_memory_key,
    stable_id,
    utcnow,
)


class SQLiteStore:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        if self.path.parent != Path("."):
            self.path.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(self.path)
        self.connection.row_factory = sqlite3.Row
        self._init_schema()

    def close(self) -> None:
        self.connection.close()

    def _init_schema(self) -> None:
        self.connection.executescript(
            """
            PRAGMA foreign_keys = ON;

            CREATE TABLE IF NOT EXISTS characters (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                personality TEXT NOT NULL,
                scenario TEXT NOT NULL,
                speaking_style TEXT NOT NULL,
                example_dialogues_json TEXT NOT NULL,
                boundaries_json TEXT NOT NULL,
                source_path TEXT,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS personas (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                profile TEXT NOT NULL,
                relationship_notes TEXT NOT NULL,
                preferences_json TEXT NOT NULL,
                tone_preferences TEXT NOT NULL,
                source_path TEXT,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                character_id TEXT NOT NULL,
                persona_id TEXT NOT NULL,
                model TEXT NOT NULL,
                title TEXT NOT NULL,
                raw_window_anchor_turn INTEGER NOT NULL DEFAULT 0,
                turn_count INTEGER NOT NULL DEFAULT 0,
                latest_summary_turn INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                turn_index INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_messages_session_turn
            ON messages (session_id, turn_index, id);

            CREATE TABLE IF NOT EXISTS summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                content TEXT NOT NULL,
                turn_index INTEGER NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_summaries_session_turn
            ON summaries (session_id, turn_index DESC, id DESC);

            CREATE TABLE IF NOT EXISTS lore_entries (
                id TEXT PRIMARY KEY,
                character_id TEXT NOT NULL,
                title TEXT NOT NULL,
                keywords_json TEXT NOT NULL,
                secondary_keywords_json TEXT NOT NULL,
                priority INTEGER NOT NULL,
                sticky_turns INTEGER NOT NULL,
                content TEXT NOT NULL,
                tags_json TEXT NOT NULL,
                enabled INTEGER NOT NULL,
                last_activated_turn INTEGER,
                updated_at TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_lore_entries_character
            ON lore_entries (character_id, priority DESC);

            CREATE TABLE IF NOT EXISTS memory_facts (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                character_id TEXT NOT NULL,
                fact_type TEXT NOT NULL,
                content TEXT NOT NULL,
                importance REAL NOT NULL,
                confidence REAL NOT NULL,
                source_turn INTEGER NOT NULL,
                last_used_turn INTEGER NOT NULL,
                pinned INTEGER NOT NULL,
                content_key TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_facts_key
            ON memory_facts (session_id, fact_type, content_key);

            CREATE INDEX IF NOT EXISTS idx_memory_facts_usage
            ON memory_facts (session_id, pinned DESC, importance DESC, last_used_turn DESC);

            CREATE TABLE IF NOT EXISTS memory_episodes (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                character_id TEXT NOT NULL,
                content TEXT NOT NULL,
                importance REAL NOT NULL,
                confidence REAL NOT NULL,
                source_turn INTEGER NOT NULL,
                last_used_turn INTEGER NOT NULL,
                content_key TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_episodes_key
            ON memory_episodes (session_id, content_key);

            CREATE INDEX IF NOT EXISTS idx_memory_episodes_usage
            ON memory_episodes (session_id, importance DESC, last_used_turn DESC);

            CREATE TABLE IF NOT EXISTS embeddings (
                owner_type TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                vector_json TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                PRIMARY KEY (owner_type, owner_id)
            );
            """
        )
        self.connection.commit()

    def upsert_character(self, character: CharacterProfile) -> None:
        now = utcnow()
        existing_lore_turns = {
            row["id"]: row["last_activated_turn"]
            for row in self.connection.execute(
                "SELECT id, last_activated_turn FROM lore_entries WHERE character_id = ?",
                (character.id,),
            ).fetchall()
        }
        self.connection.execute(
            """
            INSERT INTO characters (
                id, name, description, personality, scenario, speaking_style,
                example_dialogues_json, boundaries_json, source_path, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                personality = excluded.personality,
                scenario = excluded.scenario,
                speaking_style = excluded.speaking_style,
                example_dialogues_json = excluded.example_dialogues_json,
                boundaries_json = excluded.boundaries_json,
                source_path = excluded.source_path,
                updated_at = excluded.updated_at
            """,
            (
                character.id,
                character.name,
                character.description,
                character.personality,
                character.scenario,
                character.speaking_style,
                json.dumps(character.example_dialogues, ensure_ascii=False),
                json.dumps(character.boundaries, ensure_ascii=False),
                character.source_path,
                now,
            ),
        )
        self.connection.execute("DELETE FROM lore_entries WHERE character_id = ?", (character.id,))
        for entry in character.lorebook:
            self.connection.execute(
                """
                INSERT INTO lore_entries (
                    id, character_id, title, keywords_json, secondary_keywords_json, priority,
                    sticky_turns, content, tags_json, enabled, last_activated_turn, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    entry.id,
                    character.id,
                    entry.title,
                    json.dumps(entry.keywords, ensure_ascii=False),
                    json.dumps(entry.secondary_keywords, ensure_ascii=False),
                    entry.priority,
                    entry.sticky_turns,
                    entry.content,
                    json.dumps(entry.tags, ensure_ascii=False),
                    int(entry.enabled),
                    existing_lore_turns.get(entry.id, entry.last_activated_turn),
                    now,
                ),
            )
        self.connection.commit()

    def load_character(self, character_id: str) -> CharacterProfile | None:
        row = self.connection.execute("SELECT * FROM characters WHERE id = ?", (character_id,)).fetchone()
        if row is None:
            return None
        lorebook = self.list_lore_entries(character_id)
        return CharacterProfile(
            id=row["id"],
            name=row["name"],
            description=row["description"],
            personality=row["personality"],
            scenario=row["scenario"],
            speaking_style=row["speaking_style"],
            example_dialogues=json.loads(row["example_dialogues_json"]),
            boundaries=json.loads(row["boundaries_json"]),
            lorebook=lorebook,
            source_path=row["source_path"],
        )

    def upsert_persona(self, persona: UserPersona) -> None:
        now = utcnow()
        self.connection.execute(
            """
            INSERT INTO personas (
                id, name, profile, relationship_notes, preferences_json,
                tone_preferences, source_path, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                profile = excluded.profile,
                relationship_notes = excluded.relationship_notes,
                preferences_json = excluded.preferences_json,
                tone_preferences = excluded.tone_preferences,
                source_path = excluded.source_path,
                updated_at = excluded.updated_at
            """,
            (
                persona.id,
                persona.name,
                persona.profile,
                persona.relationship_notes,
                json.dumps(persona.preferences, ensure_ascii=False),
                persona.tone_preferences,
                persona.source_path,
                now,
            ),
        )
        self.connection.commit()

    def load_persona(self, persona_id: str) -> UserPersona | None:
        row = self.connection.execute("SELECT * FROM personas WHERE id = ?", (persona_id,)).fetchone()
        if row is None:
            return None
        return UserPersona(
            id=row["id"],
            name=row["name"],
            profile=row["profile"],
            relationship_notes=row["relationship_notes"],
            preferences=json.loads(row["preferences_json"]),
            tone_preferences=row["tone_preferences"],
            source_path=row["source_path"],
        )

    def create_session(self, character_id: str, persona_id: str, model: str, *, title: str = "") -> SessionState:
        now = utcnow()
        session = SessionState(
            id=stable_id("session", character_id, persona_id, now),
            character_id=character_id,
            persona_id=persona_id,
            model=model,
            title=title,
            created_at=now,
            updated_at=now,
        )
        self.connection.execute(
            """
            INSERT INTO sessions (
                id, character_id, persona_id, model, title,
                raw_window_anchor_turn, turn_count, latest_summary_turn, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                session.id,
                session.character_id,
                session.persona_id,
                session.model,
                session.title,
                session.raw_window_anchor_turn,
                session.turn_count,
                session.latest_summary_turn,
                session.created_at,
                session.updated_at,
            ),
        )
        self.connection.commit()
        return session

    def save_session(self, session: SessionState) -> None:
        session.updated_at = utcnow()
        self.connection.execute(
            """
            UPDATE sessions
            SET character_id = ?, persona_id = ?, model = ?, title = ?,
                raw_window_anchor_turn = ?, turn_count = ?, latest_summary_turn = ?, updated_at = ?
            WHERE id = ?
            """,
            (
                session.character_id,
                session.persona_id,
                session.model,
                session.title,
                session.raw_window_anchor_turn,
                session.turn_count,
                session.latest_summary_turn,
                session.updated_at,
                session.id,
            ),
        )
        self.connection.commit()

    def get_session(self, session_id: str) -> SessionState | None:
        row = self.connection.execute("SELECT * FROM sessions WHERE id = ?", (session_id,)).fetchone()
        return self._session_from_row(row)

    def get_latest_session(self, character_id: str, persona_id: str) -> SessionState | None:
        row = self.connection.execute(
            """
            SELECT * FROM sessions
            WHERE character_id = ? AND persona_id = ?
            ORDER BY updated_at DESC
            LIMIT 1
            """,
            (character_id, persona_id),
        ).fetchone()
        return self._session_from_row(row)

    def _session_from_row(self, row: sqlite3.Row | None) -> SessionState | None:
        if row is None:
            return None
        return SessionState(
            id=row["id"],
            character_id=row["character_id"],
            persona_id=row["persona_id"],
            model=row["model"],
            title=row["title"],
            raw_window_anchor_turn=row["raw_window_anchor_turn"],
            turn_count=row["turn_count"],
            latest_summary_turn=row["latest_summary_turn"],
            created_at=row["created_at"],
            updated_at=row["updated_at"],
        )

    def append_message(self, session_id: str, turn_index: int, role: str, content: str) -> MessageRecord:
        now = utcnow()
        cursor = self.connection.execute(
            """
            INSERT INTO messages (session_id, turn_index, role, content, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            (session_id, turn_index, role, content, now),
        )
        self.connection.commit()
        return MessageRecord(
            id=int(cursor.lastrowid),
            session_id=session_id,
            turn_index=turn_index,
            role=role,
            content=content,
            created_at=now,
        )

    def list_messages(self, session_id: str, *, after_turn: int = 0) -> list[MessageRecord]:
        rows = self.connection.execute(
            """
            SELECT * FROM messages
            WHERE session_id = ? AND turn_index > ?
            ORDER BY id ASC
            """,
            (session_id, after_turn),
        ).fetchall()
        return [self._message_from_row(row) for row in rows]

    def list_recent_messages(self, session_id: str, *, limit: int = 20, after_turn: int = 0) -> list[MessageRecord]:
        rows = self.connection.execute(
            """
            SELECT * FROM (
                SELECT * FROM messages
                WHERE session_id = ? AND turn_index > ?
                ORDER BY id DESC
                LIMIT ?
            ) ORDER BY id ASC
            """,
            (session_id, after_turn, limit),
        ).fetchall()
        return [self._message_from_row(row) for row in rows]

    def _message_from_row(self, row: sqlite3.Row) -> MessageRecord:
        return MessageRecord(
            id=row["id"],
            session_id=row["session_id"],
            turn_index=row["turn_index"],
            role=row["role"],
            content=row["content"],
            created_at=row["created_at"],
        )

    def get_latest_summary(self, session_id: str) -> SummarySnapshot | None:
        row = self.connection.execute(
            """
            SELECT * FROM summaries
            WHERE session_id = ?
            ORDER BY turn_index DESC, id DESC
            LIMIT 1
            """,
            (session_id,),
        ).fetchone()
        if row is None:
            return None
        return SummarySnapshot(
            id=row["id"],
            session_id=row["session_id"],
            content=row["content"],
            turn_index=row["turn_index"],
            created_at=row["created_at"],
        )

    def save_summary(self, session_id: str, content: str, turn_index: int) -> SummarySnapshot:
        now = utcnow()
        cursor = self.connection.execute(
            """
            INSERT INTO summaries (session_id, content, turn_index, created_at)
            VALUES (?, ?, ?, ?)
            """,
            (session_id, content, turn_index, now),
        )
        self.connection.commit()
        return SummarySnapshot(
            id=int(cursor.lastrowid),
            session_id=session_id,
            content=content,
            turn_index=turn_index,
            created_at=now,
        )

    def list_lore_entries(self, character_id: str) -> list[LoreEntry]:
        rows = self.connection.execute(
            """
            SELECT * FROM lore_entries
            WHERE character_id = ?
            ORDER BY priority DESC, title ASC
            """,
            (character_id,),
        ).fetchall()
        return [
            LoreEntry(
                id=row["id"],
                character_id=row["character_id"],
                title=row["title"],
                keywords=json.loads(row["keywords_json"]),
                secondary_keywords=json.loads(row["secondary_keywords_json"]),
                priority=row["priority"],
                sticky_turns=row["sticky_turns"],
                content=row["content"],
                tags=json.loads(row["tags_json"]),
                enabled=bool(row["enabled"]),
                last_activated_turn=row["last_activated_turn"],
            )
            for row in rows
        ]

    def mark_lore_activated(self, entry_ids: list[str], turn_index: int) -> None:
        if not entry_ids:
            return
        now = utcnow()
        self.connection.executemany(
            """
            UPDATE lore_entries
            SET last_activated_turn = ?, updated_at = ?
            WHERE id = ?
            """,
            [(turn_index, now, entry_id) for entry_id in entry_ids],
        )
        self.connection.commit()

    def list_facts(self, session_id: str, *, limit: int = 20) -> list[MemoryFact]:
        rows = self.connection.execute(
            """
            SELECT * FROM memory_facts
            WHERE session_id = ?
            ORDER BY pinned DESC, importance DESC, last_used_turn DESC, source_turn DESC
            LIMIT ?
            """,
            (session_id, limit),
        ).fetchall()
        return [self._fact_from_row(row) for row in rows]

    def upsert_fact(
        self,
        session_id: str,
        character_id: str,
        candidate: MemoryCandidate,
        *,
        turn_index: int,
        pinned: bool = False,
    ) -> MemoryFact:
        now = utcnow()
        content_key = normalize_memory_key(candidate.content)
        row = self.connection.execute(
            """
            SELECT * FROM memory_facts
            WHERE session_id = ? AND fact_type = ? AND content_key = ?
            """,
            (session_id, candidate.kind, content_key),
        ).fetchone()
        if row is None:
            fact = MemoryFact(
                id=stable_id("fact", session_id, candidate.kind, candidate.content),
                session_id=session_id,
                character_id=character_id,
                fact_type=candidate.kind,
                content=candidate.content.strip(),
                importance=float(candidate.importance),
                confidence=float(candidate.confidence),
                source_turn=turn_index,
                last_used_turn=turn_index,
                pinned=pinned,
                created_at=now,
                updated_at=now,
            )
            self.connection.execute(
                """
                INSERT INTO memory_facts (
                    id, session_id, character_id, fact_type, content, importance, confidence,
                    source_turn, last_used_turn, pinned, content_key, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    fact.id,
                    fact.session_id,
                    fact.character_id,
                    fact.fact_type,
                    fact.content,
                    fact.importance,
                    fact.confidence,
                    fact.source_turn,
                    fact.last_used_turn,
                    int(fact.pinned),
                    content_key,
                    fact.created_at,
                    fact.updated_at,
                ),
            )
            self.connection.commit()
            return fact

        importance = max(float(row["importance"]), float(candidate.importance))
        confidence = max(float(row["confidence"]), float(candidate.confidence))
        pinned_value = int(bool(row["pinned"]) or pinned)
        self.connection.execute(
            """
            UPDATE memory_facts
            SET importance = ?, confidence = ?, last_used_turn = ?, pinned = ?, updated_at = ?
            WHERE id = ?
            """,
            (importance, confidence, turn_index, pinned_value, now, row["id"]),
        )
        self.connection.commit()
        updated = self.connection.execute("SELECT * FROM memory_facts WHERE id = ?", (row["id"],)).fetchone()
        if updated is None:
            raise RuntimeError("Failed to reload updated fact.")
        return self._fact_from_row(updated)

    def touch_facts(self, fact_ids: list[str], turn_index: int) -> None:
        if not fact_ids:
            return
        now = utcnow()
        self.connection.executemany(
            """
            UPDATE memory_facts
            SET last_used_turn = ?, updated_at = ?
            WHERE id = ?
            """,
            [(turn_index, now, fact_id) for fact_id in fact_ids],
        )
        self.connection.commit()

    def _fact_from_row(self, row: sqlite3.Row) -> MemoryFact:
        return MemoryFact(
            id=row["id"],
            session_id=row["session_id"],
            character_id=row["character_id"],
            fact_type=row["fact_type"],
            content=row["content"],
            importance=float(row["importance"]),
            confidence=float(row["confidence"]),
            source_turn=row["source_turn"],
            last_used_turn=row["last_used_turn"],
            pinned=bool(row["pinned"]),
            created_at=row["created_at"],
            updated_at=row["updated_at"],
        )

    def list_episodes(self, session_id: str, *, limit: int = 100) -> list[MemoryEpisode]:
        rows = self.connection.execute(
            """
            SELECT * FROM memory_episodes
            WHERE session_id = ?
            ORDER BY importance DESC, last_used_turn DESC, source_turn DESC
            LIMIT ?
            """,
            (session_id, limit),
        ).fetchall()
        return [self._episode_from_row(row) for row in rows]

    def save_episode(
        self,
        session_id: str,
        character_id: str,
        candidate: MemoryCandidate,
        *,
        turn_index: int,
    ) -> MemoryEpisode:
        now = utcnow()
        content_key = normalize_memory_key(candidate.content)
        row = self.connection.execute(
            """
            SELECT * FROM memory_episodes
            WHERE session_id = ? AND content_key = ?
            """,
            (session_id, content_key),
        ).fetchone()
        if row is None:
            episode = MemoryEpisode(
                id=stable_id("episode", session_id, candidate.content),
                session_id=session_id,
                character_id=character_id,
                content=candidate.content.strip(),
                importance=float(candidate.importance),
                confidence=float(candidate.confidence),
                source_turn=turn_index,
                last_used_turn=turn_index,
                created_at=now,
                updated_at=now,
            )
            self.connection.execute(
                """
                INSERT INTO memory_episodes (
                    id, session_id, character_id, content, importance, confidence,
                    source_turn, last_used_turn, content_key, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    episode.id,
                    episode.session_id,
                    episode.character_id,
                    episode.content,
                    episode.importance,
                    episode.confidence,
                    episode.source_turn,
                    episode.last_used_turn,
                    content_key,
                    episode.created_at,
                    episode.updated_at,
                ),
            )
            self.connection.commit()
            return episode

        importance = max(float(row["importance"]), float(candidate.importance))
        confidence = max(float(row["confidence"]), float(candidate.confidence))
        self.connection.execute(
            """
            UPDATE memory_episodes
            SET importance = ?, confidence = ?, last_used_turn = ?, updated_at = ?
            WHERE id = ?
            """,
            (importance, confidence, turn_index, now, row["id"]),
        )
        self.connection.commit()
        updated = self.connection.execute("SELECT * FROM memory_episodes WHERE id = ?", (row["id"],)).fetchone()
        if updated is None:
            raise RuntimeError("Failed to reload updated episode.")
        return self._episode_from_row(updated)

    def touch_episodes(self, episode_ids: list[str], turn_index: int) -> None:
        if not episode_ids:
            return
        now = utcnow()
        self.connection.executemany(
            """
            UPDATE memory_episodes
            SET last_used_turn = ?, updated_at = ?
            WHERE id = ?
            """,
            [(turn_index, now, episode_id) for episode_id in episode_ids],
        )
        self.connection.commit()

    def _episode_from_row(self, row: sqlite3.Row) -> MemoryEpisode:
        return MemoryEpisode(
            id=row["id"],
            session_id=row["session_id"],
            character_id=row["character_id"],
            content=row["content"],
            importance=float(row["importance"]),
            confidence=float(row["confidence"]),
            source_turn=row["source_turn"],
            last_used_turn=row["last_used_turn"],
            created_at=row["created_at"],
            updated_at=row["updated_at"],
        )

    def save_embedding(self, owner_type: str, owner_id: str, vector: list[float]) -> None:
        self.connection.execute(
            """
            INSERT INTO embeddings (owner_type, owner_id, vector_json, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(owner_type, owner_id) DO UPDATE SET
                vector_json = excluded.vector_json,
                updated_at = excluded.updated_at
            """,
            (owner_type, owner_id, json.dumps(vector), utcnow()),
        )
        self.connection.commit()

    def get_embedding(self, owner_type: str, owner_id: str) -> list[float] | None:
        row = self.connection.execute(
            """
            SELECT vector_json FROM embeddings
            WHERE owner_type = ? AND owner_id = ?
            """,
            (owner_type, owner_id),
        ).fetchone()
        if row is None:
            return None
        data = json.loads(row["vector_json"])
        return [float(item) for item in data]

    def list_embeddings(self, owner_type: str, owner_ids: list[str]) -> dict[str, list[float]]:
        if not owner_ids:
            return {}
        placeholders = ", ".join("?" for _ in owner_ids)
        rows = self.connection.execute(
            f"""
            SELECT owner_id, vector_json FROM embeddings
            WHERE owner_type = ? AND owner_id IN ({placeholders})
            """,
            [owner_type, *owner_ids],
        ).fetchall()
        return {row["owner_id"]: [float(item) for item in json.loads(row["vector_json"])] for row in rows}
