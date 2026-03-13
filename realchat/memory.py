from __future__ import annotations

import json
import re

from .embeddings import EmbeddingBackend, cosine_similarity
from .llm import ChatBackend
from .models import (
    CharacterProfile,
    LoreEntry,
    MemoryCandidate,
    MemoryEpisode,
    MemoryExtractionResult,
    MemoryFact,
    MessageRecord,
    SessionState,
    SummarySnapshot,
    UserPersona,
    estimate_tokens,
    normalize_memory_key,
)
from .prompting import PromptBuilder
from .storage import SQLiteStore


class MemoryPipeline:
    def __init__(
        self,
        *,
        store: SQLiteStore,
        chat_backend: ChatBackend,
        embedding_backend: EmbeddingBackend,
        prompt_builder: PromptBuilder,
        summary_turn_interval: int = 12,
        recent_turns_kept: int = 4,
        total_context_tokens: int = 12_000,
    ) -> None:
        self.store = store
        self.chat_backend = chat_backend
        self.embedding_backend = embedding_backend
        self.prompt_builder = prompt_builder
        self.summary_turn_interval = summary_turn_interval
        self.recent_turns_kept = recent_turns_kept
        self.total_context_tokens = total_context_tokens

    def retrieve_memories(
        self,
        *,
        session: SessionState,
        user_input: str,
        recent_messages: list[MessageRecord],
        active_lore_entries: list[LoreEntry],
    ) -> tuple[list[MemoryFact], list[MemoryEpisode]]:
        query_text = self._build_query_text(
            user_input=user_input,
            recent_messages=recent_messages,
            active_lore_entries=active_lore_entries,
        )
        fact_candidates = self.store.list_facts(session.id, limit=25)
        facts = self._rank_facts(session=session, query_text=query_text, facts=fact_candidates)
        episodes = self._rank_episodes(
            session=session,
            query_text=query_text,
            episodes=self.store.list_episodes(session.id, limit=80),
            top_k=6,
        )
        self.store.touch_facts([fact.id for fact in facts], session.turn_count)
        self.store.touch_episodes([episode.id for episode in episodes], session.turn_count)
        return facts, episodes

    def process_turn(
        self,
        *,
        session: SessionState,
        character: CharacterProfile,
        persona: UserPersona,
        user_input: str,
        assistant_reply: str,
        model: str,
    ) -> SummarySnapshot | None:
        extraction = self.extract_memories(
            character=character,
            persona=persona,
            summary=self.store.get_latest_summary(session.id),
            user_input=user_input,
            assistant_reply=assistant_reply,
            model=model,
        )
        for candidate in extraction.stable_facts:
            self.store.upsert_fact(
                session.id,
                character.id,
                MemoryCandidate(
                    content=candidate.content,
                    importance=candidate.importance,
                    confidence=candidate.confidence,
                    kind="stable_fact",
                ),
                turn_index=session.turn_count,
            )
        for candidate in extraction.relationship_state:
            self.store.upsert_fact(
                session.id,
                character.id,
                MemoryCandidate(
                    content=candidate.content,
                    importance=candidate.importance,
                    confidence=candidate.confidence,
                    kind="relationship_state",
                ),
                turn_index=session.turn_count,
            )
        for candidate in extraction.episodes:
            episode = self.store.save_episode(
                session.id,
                character.id,
                MemoryCandidate(
                    content=candidate.content,
                    importance=candidate.importance,
                    confidence=candidate.confidence,
                    kind="episode",
                ),
                turn_index=session.turn_count,
            )
            self.store.save_embedding("episode", episode.id, self.embedding_backend.embed_text(episode.content))
        return self.maybe_refresh_summary(session=session, character=character, persona=persona, model=model)

    def extract_memories(
        self,
        *,
        character: CharacterProfile,
        persona: UserPersona,
        summary: SummarySnapshot | None,
        user_input: str,
        assistant_reply: str,
        model: str,
    ) -> MemoryExtractionResult:
        messages = [
            {
                "role": "system",
                "content": (
                    "Extract long-term roleplay memory from the latest exchange. "
                    "Return strict JSON with keys stable_facts, relationship_state, episodes. "
                    "Each list item must be either a string or an object with content, importance, confidence. "
                    "Only keep durable information that improves future continuity."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"Character: {character.name}\n"
                    f"Character personality: {character.personality}\n"
                    f"User persona: {persona.profile or persona.name}\n"
                    f"Existing summary: {summary.content if summary else '(none)'}\n"
                    f"Latest user message: {user_input}\n"
                    f"Latest assistant reply: {assistant_reply}"
                ),
            },
        ]
        try:
            raw = self.chat_backend.complete(messages, model, temperature=0.1)
            return self._parse_extraction(raw)
        except Exception:
            return self._fallback_extraction(user_input=user_input, assistant_reply=assistant_reply)

    def maybe_refresh_summary(
        self,
        *,
        session: SessionState,
        character: CharacterProfile,
        persona: UserPersona,
        model: str,
    ) -> SummarySnapshot | None:
        current_summary = self.store.get_latest_summary(session.id)
        raw_messages = self.store.list_messages(session.id, after_turn=session.raw_window_anchor_turn)
        raw_tokens = sum(estimate_tokens(message.content) for message in raw_messages)
        should_refresh = (
            session.turn_count - session.latest_summary_turn >= self.summary_turn_interval
            or raw_tokens > int(self.total_context_tokens * 0.60)
        )
        if not should_refresh:
            return current_summary

        since_turn = current_summary.turn_index if current_summary is not None else 0
        delta_messages = self.store.list_messages(session.id, after_turn=since_turn)
        transcript = self._format_transcript(delta_messages)
        messages = [
            {
                "role": "system",
                "content": (
                    "Summarize the conversation for long-term continuity. Preserve relationship status, "
                    "stable preferences, unresolved threads, emotional shifts, and important events. "
                    "Write one compact paragraph under 220 words."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"Character: {character.name}\n"
                    f"User persona: {persona.name}\n"
                    f"Previous summary: {current_summary.content if current_summary else '(none)'}\n"
                    f"New transcript:\n{transcript}"
                ),
            },
        ]
        try:
            summary_text = self.chat_backend.complete(messages, model, temperature=0.1).strip()
        except Exception:
            summary_text = ""
        if not summary_text:
            summary_text = self._fallback_summary(delta_messages, current_summary=current_summary)

        snapshot = self.store.save_summary(session.id, summary_text, session.turn_count)
        session.latest_summary_turn = session.turn_count
        session.raw_window_anchor_turn = max(0, session.turn_count - self.recent_turns_kept)
        self.store.save_session(session)
        return snapshot

    def _rank_facts(
        self,
        *,
        session: SessionState,
        query_text: str,
        facts: list[MemoryFact],
    ) -> list[MemoryFact]:
        query_terms = set(re.findall(r"[\w\u4e00-\u9fff]+", normalize_memory_key(query_text)))
        scored: list[tuple[float, MemoryFact]] = []
        for fact in facts:
            fact_terms = set(re.findall(r"[\w\u4e00-\u9fff]+", normalize_memory_key(fact.content)))
            overlap = len(query_terms & fact_terms) / max(1, len(query_terms))
            recency_turn = fact.last_used_turn or fact.source_turn
            recency = 1.0 / max(1, session.turn_count - recency_turn + 1)
            score = (
                fact.importance * 0.45
                + fact.confidence * 0.10
                + overlap * 0.25
                + recency * 0.10
                + (0.20 if fact.pinned else 0.0)
            )
            scored.append((score, fact))
        scored.sort(key=lambda item: item[0], reverse=True)
        return [fact for _, fact in scored[:4] if _ > 0.05]

    def _rank_episodes(
        self,
        *,
        session: SessionState,
        query_text: str,
        episodes: list[MemoryEpisode],
        top_k: int,
    ) -> list[MemoryEpisode]:
        if not episodes:
            return []
        query_vector = self.embedding_backend.embed_text(query_text)
        embeddings = self.store.list_embeddings("episode", [episode.id for episode in episodes])
        scored: list[tuple[float, MemoryEpisode]] = []
        for episode in episodes:
            vector = embeddings.get(episode.id)
            if vector is None:
                vector = self.embedding_backend.embed_text(episode.content)
                self.store.save_embedding("episode", episode.id, vector)
            similarity = cosine_similarity(query_vector, vector)
            recency_turn = episode.last_used_turn or episode.source_turn
            recency = 1.0 / max(1, session.turn_count - recency_turn + 1)
            score = similarity * 0.60 + episode.importance * 0.25 + recency * 0.15
            scored.append((score, episode))
        scored.sort(key=lambda item: item[0], reverse=True)
        return [episode for score, episode in scored[:top_k] if score > 0.0]

    def _build_query_text(
        self,
        *,
        user_input: str,
        recent_messages: list[MessageRecord],
        active_lore_entries: list[LoreEntry],
    ) -> str:
        snippets = [user_input]
        snippets.extend(message.content for message in recent_messages[-4:])
        tags = []
        for entry in active_lore_entries:
            tags.extend(entry.tags)
        if tags:
            snippets.append("Lore tags: " + ", ".join(sorted(set(tags))))
        return "\n".join(part for part in snippets if part.strip())

    def _parse_extraction(self, raw_text: str) -> MemoryExtractionResult:
        cleaned = raw_text.strip()
        if cleaned.startswith("```"):
            cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
            cleaned = re.sub(r"\s*```$", "", cleaned)
        start = cleaned.find("{")
        end = cleaned.rfind("}")
        if start >= 0 and end >= start:
            cleaned = cleaned[start : end + 1]
        payload = json.loads(cleaned)
        if not isinstance(payload, dict):
            raise ValueError("Extraction response must be a JSON object.")
        return MemoryExtractionResult(
            stable_facts=self._normalize_candidates(payload.get("stable_facts"), kind="stable_fact"),
            relationship_state=self._normalize_candidates(
                payload.get("relationship_state"),
                kind="relationship_state",
            ),
            episodes=self._normalize_candidates(payload.get("episodes"), kind="episode"),
        )

    def _normalize_candidates(self, payload: object, *, kind: str) -> list[MemoryCandidate]:
        if not isinstance(payload, list):
            return []
        results: list[MemoryCandidate] = []
        for item in payload:
            if isinstance(item, str):
                content = item.strip()
                if content:
                    results.append(MemoryCandidate(content=content, importance=0.6, confidence=0.6, kind=kind))
                continue
            if not isinstance(item, dict):
                continue
            content = str(item.get("content", item.get("text", ""))).strip()
            if not content:
                continue
            importance = self._clamp(float(item.get("importance", 0.6) or 0.6))
            confidence = self._clamp(float(item.get("confidence", 0.7) or 0.7))
            results.append(
                MemoryCandidate(
                    content=content,
                    importance=importance,
                    confidence=confidence,
                    kind=kind,
                )
            )
        return results

    def _fallback_extraction(self, *, user_input: str, assistant_reply: str) -> MemoryExtractionResult:
        stable_facts: list[MemoryCandidate] = []
        patterns = (
            r"\bmy name is ([A-Za-z\u4e00-\u9fff]+)",
            r"\bcall me ([A-Za-z\u4e00-\u9fff]+)",
            r"\bi like ([^.!?\n]+)",
            r"\bi am from ([^.!?\n]+)",
        )
        lower_input = user_input.lower()
        for pattern in patterns:
            match = re.search(pattern, lower_input)
            if match:
                stable_facts.append(
                    MemoryCandidate(
                        content=match.group(0).strip().capitalize(),
                        importance=0.75,
                        confidence=0.45,
                        kind="stable_fact",
                    )
                )
        episode_text = f"User said: {user_input.strip()} | Assistant replied: {assistant_reply.strip()}"
        episodes = [
            MemoryCandidate(
                content=episode_text[:500],
                importance=0.45,
                confidence=0.30,
                kind="episode",
            )
        ]
        return MemoryExtractionResult(stable_facts=stable_facts, episodes=episodes)

    def _fallback_summary(
        self,
        messages: list[MessageRecord],
        *,
        current_summary: SummarySnapshot | None,
    ) -> str:
        fragments = [current_summary.content] if current_summary is not None else []
        fragments.extend(f"{message.role}: {message.content}" for message in messages[-8:])
        return " | ".join(fragment for fragment in fragments if fragment).strip()[:800]

    def _format_transcript(self, messages: list[MessageRecord]) -> str:
        lines = [f"{message.role.upper()}: {message.content}" for message in messages[-24:]]
        return "\n".join(lines)

    def _clamp(self, value: float) -> float:
        return max(0.0, min(1.0, value))
