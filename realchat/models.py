from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re


def utcnow() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def slugify(value: str, *, default: str = "item") -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.strip().lower())
    normalized = normalized.strip("-")
    return normalized or default


def stable_id(prefix: str, *parts: str) -> str:
    digest = hashlib.sha1("::".join(parts).encode("utf-8")).hexdigest()[:12]
    return f"{prefix}-{digest}"


def estimate_tokens(text: str) -> int:
    if not text:
        return 0
    return max(1, len(text) // 4)


def normalize_memory_key(text: str) -> str:
    collapsed = re.sub(r"\s+", " ", text.strip().lower())
    return re.sub(r"[^a-z0-9\u4e00-\u9fff ]+", "", collapsed)


def _ensure_list(value: object) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [value] if value.strip() else []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    return [str(value).strip()] if str(value).strip() else []


@dataclass(slots=True)
class LoreEntry:
    id: str
    character_id: str
    title: str
    keywords: list[str] = field(default_factory=list)
    secondary_keywords: list[str] = field(default_factory=list)
    priority: int = 0
    sticky_turns: int = 0
    content: str = ""
    tags: list[str] = field(default_factory=list)
    enabled: bool = True
    last_activated_turn: int | None = None

    @classmethod
    def from_dict(cls, payload: dict[str, object], character_id: str, index: int = 0) -> "LoreEntry":
        title = str(payload.get("title", "")).strip() or f"Lore {index + 1}"
        entry_id = str(payload.get("id", "")).strip() or stable_id("lore", character_id, title, str(index))
        return cls(
            id=entry_id,
            character_id=character_id,
            title=title,
            keywords=[item.lower() for item in _ensure_list(payload.get("keywords"))],
            secondary_keywords=[item.lower() for item in _ensure_list(payload.get("secondary_keywords"))],
            priority=int(payload.get("priority", 0) or 0),
            sticky_turns=int(payload.get("sticky_turns", 0) or 0),
            content=str(payload.get("content", "")).strip(),
            tags=[item.lower() for item in _ensure_list(payload.get("tags"))],
            enabled=bool(payload.get("enabled", True)),
            last_activated_turn=(
                int(payload["last_activated_turn"])
                if payload.get("last_activated_turn") is not None
                else None
            ),
        )


@dataclass(slots=True)
class CharacterProfile:
    id: str
    name: str
    description: str = ""
    personality: str = ""
    scenario: str = ""
    speaking_style: str = ""
    example_dialogues: list[str] = field(default_factory=list)
    boundaries: list[str] = field(default_factory=list)
    lorebook: list[LoreEntry] = field(default_factory=list)
    source_path: str | None = None

    @classmethod
    def from_dict(cls, payload: dict[str, object], *, source_path: Path | None = None) -> "CharacterProfile":
        source_label = str(source_path.resolve()) if source_path is not None else ""
        name = str(payload.get("name", "")).strip() or "Assistant"
        character_id = str(payload.get("id", "")).strip() or stable_id(
            "char",
            source_label or name,
            name,
        )
        lorebook = [
            LoreEntry.from_dict(item, character_id, index)
            for index, item in enumerate(payload.get("lorebook", []) or [])
            if isinstance(item, dict)
        ]
        return cls(
            id=character_id,
            name=name,
            description=str(payload.get("description", "")).strip(),
            personality=str(payload.get("personality", "")).strip(),
            scenario=str(payload.get("scenario", "")).strip(),
            speaking_style=str(payload.get("speaking_style", "")).strip(),
            example_dialogues=_ensure_list(payload.get("example_dialogues")),
            boundaries=_ensure_list(payload.get("boundaries")),
            lorebook=lorebook,
            source_path=source_label or None,
        )

    @classmethod
    def from_json_file(cls, path: str | Path) -> "CharacterProfile":
        source_path = Path(path)
        payload = json.loads(source_path.read_text(encoding="utf-8"))
        if not isinstance(payload, dict):
            raise ValueError("Character JSON must be an object.")
        return cls.from_dict(payload, source_path=source_path)


@dataclass(slots=True)
class UserPersona:
    id: str
    name: str
    profile: str = ""
    relationship_notes: str = ""
    preferences: list[str] = field(default_factory=list)
    tone_preferences: str = ""
    source_path: str | None = None

    @classmethod
    def from_dict(cls, payload: dict[str, object], *, source_path: Path | None = None) -> "UserPersona":
        source_label = str(source_path.resolve()) if source_path is not None else ""
        name = str(payload.get("name", "")).strip() or "User"
        persona_id = str(payload.get("id", "")).strip() or stable_id(
            "persona",
            source_label or name,
            name,
        )
        return cls(
            id=persona_id,
            name=name,
            profile=str(payload.get("profile", "")).strip(),
            relationship_notes=str(payload.get("relationship_notes", "")).strip(),
            preferences=_ensure_list(payload.get("preferences")),
            tone_preferences=str(payload.get("tone_preferences", "")).strip(),
            source_path=source_label or None,
        )

    @classmethod
    def from_json_file(cls, path: str | Path) -> "UserPersona":
        source_path = Path(path)
        payload = json.loads(source_path.read_text(encoding="utf-8"))
        if not isinstance(payload, dict):
            raise ValueError("Persona JSON must be an object.")
        return cls.from_dict(payload, source_path=source_path)


@dataclass(slots=True)
class SessionState:
    id: str
    character_id: str
    persona_id: str
    model: str
    title: str = ""
    raw_window_anchor_turn: int = 0
    turn_count: int = 0
    latest_summary_turn: int = 0
    created_at: str = field(default_factory=utcnow)
    updated_at: str = field(default_factory=utcnow)


@dataclass(slots=True)
class MessageRecord:
    id: int
    session_id: str
    turn_index: int
    role: str
    content: str
    created_at: str

    def as_chat_message(self) -> dict[str, str]:
        return {"role": self.role, "content": self.content}


@dataclass(slots=True)
class SummarySnapshot:
    id: int
    session_id: str
    content: str
    turn_index: int
    created_at: str


@dataclass(slots=True)
class MemoryFact:
    id: str
    session_id: str
    character_id: str
    fact_type: str
    content: str
    importance: float
    confidence: float
    source_turn: int
    last_used_turn: int
    pinned: bool
    created_at: str
    updated_at: str


@dataclass(slots=True)
class MemoryEpisode:
    id: str
    session_id: str
    character_id: str
    content: str
    importance: float
    confidence: float
    source_turn: int
    last_used_turn: int
    created_at: str
    updated_at: str


@dataclass(slots=True)
class PromptSection:
    name: str
    content: str


@dataclass(slots=True)
class PromptBundle:
    messages: list[dict[str, str]]
    sections: list[PromptSection]
    active_lore_entries: list[LoreEntry]
    retrieved_facts: list[MemoryFact]
    retrieved_episodes: list[MemoryEpisode]
    summary: SummarySnapshot | None
    persona_invariants: str


@dataclass(slots=True)
class MemoryCandidate:
    content: str
    importance: float = 0.5
    confidence: float = 0.6
    kind: str = "stable_fact"


@dataclass(slots=True)
class MemoryExtractionResult:
    stable_facts: list[MemoryCandidate] = field(default_factory=list)
    relationship_state: list[MemoryCandidate] = field(default_factory=list)
    episodes: list[MemoryCandidate] = field(default_factory=list)

