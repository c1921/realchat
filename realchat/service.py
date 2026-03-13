from __future__ import annotations

from dataclasses import dataclass

from .llm import ChatBackend
from .memory import MemoryPipeline
from .models import (
    CharacterProfile,
    MemoryCandidate,
    PromptBundle,
    SessionState,
    UserPersona,
    stable_id,
)
from .prompting import PromptBuilder
from .storage import SQLiteStore


@dataclass(slots=True)
class SessionContext:
    session: SessionState
    character: CharacterProfile
    persona: UserPersona
    base_system_prompt: str


class ChatService:
    def __init__(
        self,
        *,
        store: SQLiteStore,
        chat_backend: ChatBackend,
        prompt_builder: PromptBuilder,
        memory_pipeline: MemoryPipeline,
    ) -> None:
        self.store = store
        self.chat_backend = chat_backend
        self.prompt_builder = prompt_builder
        self.memory_pipeline = memory_pipeline

    def load_context(
        self,
        *,
        model: str,
        base_system_prompt: str,
        character_path: str | None = None,
        persona_path: str | None = None,
        session_id: str | None = None,
    ) -> SessionContext:
        if session_id:
            session = self.store.get_session(session_id)
            if session is None:
                raise ValueError(f"Unknown session: {session_id}")
            character = self.store.load_character(session.character_id)
            persona = self.store.load_persona(session.persona_id)
            if character is None or persona is None:
                raise ValueError("Session references missing character or persona.")
            return SessionContext(
                session=session,
                character=character,
                persona=persona,
                base_system_prompt=base_system_prompt,
            )

        character = (
            CharacterProfile.from_json_file(character_path)
            if character_path
            else self._default_character(base_system_prompt)
        )
        persona = (
            UserPersona.from_json_file(persona_path)
            if persona_path
            else self._default_persona()
        )
        self.store.upsert_character(character)
        self.store.upsert_persona(persona)
        session = self.store.get_latest_session(character.id, persona.id)
        if session is None:
            session = self.store.create_session(character.id, persona.id, model, title=character.name)
        else:
            session.model = model
            self.store.save_session(session)
        return SessionContext(
            session=session,
            character=character,
            persona=persona,
            base_system_prompt=base_system_prompt,
        )

    def build_prompt_bundle(self, context: SessionContext, user_input: str) -> PromptBundle:
        recent_messages = self.store.list_messages(
            context.session.id,
            after_turn=context.session.raw_window_anchor_turn,
        )
        summary = self.store.get_latest_summary(context.session.id)
        lore_entries = self.store.list_lore_entries(context.character.id)
        active_lore_entries = self.prompt_builder.activate_lore_entries(
            lore_entries,
            user_input,
            recent_messages,
            current_turn=context.session.turn_count,
        )
        facts, episodes = self.memory_pipeline.retrieve_memories(
            session=context.session,
            user_input=user_input,
            recent_messages=recent_messages,
            active_lore_entries=active_lore_entries,
        )
        return self.prompt_builder.build_prompt(
            base_system_prompt=context.base_system_prompt,
            character=context.character,
            persona=context.persona,
            summary=summary,
            lore_entries=active_lore_entries,
            facts=facts,
            episodes=episodes,
            recent_messages=recent_messages,
            user_input=user_input,
        )

    def generate_reply(self, context: SessionContext, user_input: str) -> str:
        prompt_bundle = self.build_prompt_bundle(context, user_input)
        reply = self.chat_backend.complete(
            prompt_bundle.messages,
            context.session.model,
            temperature=0.7,
        )
        conflicts = self.prompt_builder.detect_persona_conflicts(reply, context.character)
        if conflicts:
            retry_messages = list(prompt_bundle.messages)
            insert_at = next((index for index, item in enumerate(retry_messages) if item["role"] != "system"), len(retry_messages) - 1)
            retry_messages.insert(
                insert_at,
                {
                    "role": "system",
                    "content": (
                        "Your prior draft drifted away from the active character. "
                        + " ".join(conflicts)
                        + f" Rewrite the reply while preserving these invariants: {prompt_bundle.persona_invariants}"
                    ),
                },
            )
            reply = self.chat_backend.complete(
                retry_messages,
                context.session.model,
                temperature=0.4,
            )

        next_turn = context.session.turn_count + 1
        self.store.append_message(context.session.id, next_turn, "user", user_input)
        self.store.append_message(context.session.id, next_turn, "assistant", reply)
        context.session.turn_count = next_turn
        self.store.save_session(context.session)
        if prompt_bundle.active_lore_entries:
            self.store.mark_lore_activated(
                [entry.id for entry in prompt_bundle.active_lore_entries],
                context.session.turn_count,
            )
        try:
            self.memory_pipeline.process_turn(
                session=context.session,
                character=context.character,
                persona=context.persona,
                user_input=user_input,
                assistant_reply=reply,
                model=context.session.model,
            )
        except Exception:
            pass
        return reply

    def handle_command(self, context: SessionContext, command_text: str) -> str | None:
        command, _, arguments = command_text.partition(" ")
        normalized = command.lower()
        if normalized == "/clear":
            context.session.raw_window_anchor_turn = context.session.turn_count
            self.store.save_session(context.session)
            return "Recent context cleared. Long-term memory preserved."
        if normalized == "/summary":
            summary = self.store.get_latest_summary(context.session.id)
            return summary.content if summary is not None else "No summary available yet."
        if normalized == "/facts":
            facts = self.store.list_facts(context.session.id, limit=12)
            if not facts:
                return "No long-term facts stored yet."
            return "\n".join(
                f"{'[PIN] ' if fact.pinned else ''}{fact.fact_type}: {fact.content}"
                for fact in facts
            )
        if normalized == "/memory":
            episodes = self.store.list_episodes(context.session.id, limit=8)
            if not episodes:
                return "No episodic memory stored yet."
            return "\n".join(f"turn {episode.source_turn}: {episode.content}" for episode in episodes)
        if normalized == "/pin":
            if not arguments.strip():
                return "Usage: /pin <text>"
            self.store.upsert_fact(
                context.session.id,
                context.character.id,
                MemoryCandidate(
                    content=arguments.strip(),
                    importance=1.0,
                    confidence=1.0,
                    kind="stable_fact",
                ),
                turn_index=max(1, context.session.turn_count),
                pinned=True,
            )
            return "Pinned fact stored."
        if normalized == "/lore":
            entries = self.store.list_lore_entries(context.character.id)
            if not entries:
                return "No lorebook entries configured."
            return "\n".join(
                f"{entry.title}: keywords={', '.join(entry.keywords) or '-'} | sticky={entry.sticky_turns}"
                for entry in entries
            )
        if normalized == "/new-session":
            context.session = self.store.create_session(
                context.character.id,
                context.persona.id,
                context.session.model,
                title=context.character.name,
            )
            return f"Started new session {context.session.id}."
        return None

    def _default_character(self, base_system_prompt: str) -> CharacterProfile:
        return CharacterProfile(
            id=stable_id("char", "default", base_system_prompt),
            name="Assistant",
            description="A helpful AI assistant.",
            personality="Helpful, attentive, and consistent.",
            scenario=base_system_prompt,
            speaking_style="Clear and direct.",
        )

    def _default_persona(self) -> UserPersona:
        return UserPersona(
            id=stable_id("persona", "default-user"),
            name="User",
        )
