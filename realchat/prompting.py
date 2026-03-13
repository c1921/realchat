from __future__ import annotations

import re

from .models import (
    CharacterProfile,
    LoreEntry,
    MemoryEpisode,
    MemoryFact,
    MessageRecord,
    PromptBundle,
    PromptSection,
    SummarySnapshot,
    UserPersona,
    estimate_tokens,
)


GENERIC_AI_PHRASES = (
    "as an ai",
    "language model",
    "i'm just an ai",
    "i am just an ai",
    "chatgpt",
    "deepseek assistant",
    "cannot roleplay",
)


class PromptBuilder:
    def __init__(self, *, total_context_tokens: int = 12_000) -> None:
        self.total_context_tokens = total_context_tokens
        self.system_budget = int(total_context_tokens * 0.10)
        self.summary_budget = int(total_context_tokens * 0.15)
        self.memory_budget = int(total_context_tokens * 0.15)
        self.raw_budget = int(total_context_tokens * 0.60)

    def activate_lore_entries(
        self,
        entries: list[LoreEntry],
        user_input: str,
        recent_messages: list[MessageRecord],
        *,
        current_turn: int,
    ) -> list[LoreEntry]:
        recent_text = "\n".join(message.content for message in recent_messages[-20:])
        combined = f"{recent_text}\n{user_input}".lower()
        active: list[LoreEntry] = []
        for entry in entries:
            if not entry.enabled:
                continue
            sticky = (
                entry.last_activated_turn is not None
                and current_turn - entry.last_activated_turn <= entry.sticky_turns
            )
            primary_match = any(keyword in combined for keyword in entry.keywords) if entry.keywords else False
            secondary_match = (
                any(keyword in combined for keyword in entry.secondary_keywords)
                if entry.secondary_keywords
                else True
            )
            if sticky or (primary_match and secondary_match):
                active.append(entry)
        active.sort(key=lambda entry: (-entry.priority, entry.title))
        return active

    def build_prompt(
        self,
        *,
        base_system_prompt: str,
        character: CharacterProfile,
        persona: UserPersona,
        summary: SummarySnapshot | None,
        lore_entries: list[LoreEntry],
        facts: list[MemoryFact],
        episodes: list[MemoryEpisode],
        recent_messages: list[MessageRecord],
        user_input: str,
    ) -> PromptBundle:
        sections: list[PromptSection] = []
        base_budget = max(80, int(self.system_budget * 0.30))
        persona_budget = max(80, int(self.system_budget * 0.25))
        character_budget = max(120, self.system_budget - base_budget - persona_budget)
        base_system = self._truncate(base_system_prompt.strip(), base_budget, keep="head")
        if base_system:
            sections.append(PromptSection(name="base_system", content=base_system))

        persona_text = self._format_persona(persona)
        persona_text = self._truncate(persona_text, persona_budget, keep="head")
        if persona_text:
            sections.append(PromptSection(name="persona", content=persona_text))

        character_text = self._format_character(character)
        character_text = self._truncate(character_text, character_budget, keep="head")
        if character_text:
            sections.append(PromptSection(name="character_core", content=character_text))

        if summary is not None:
            summary_text = self._truncate(f"Session summary:\n{summary.content}", self.summary_budget, keep="head")
            if summary_text:
                sections.append(PromptSection(name="summary", content=summary_text))

        lore_text = self._truncate_items(
            [self._format_lore_entry(entry) for entry in lore_entries],
            self.memory_budget // 2,
        )
        if lore_text:
            sections.append(PromptSection(name="lore", content=f"Active lorebook entries:\n{lore_text}"))

        memory_text = self._truncate_items(
            self._format_memory_items(facts=facts, episodes=episodes),
            self.memory_budget,
        )
        if memory_text:
            sections.append(PromptSection(name="memory", content=f"Relevant long-term memory:\n{memory_text}"))

        persona_invariants = self.build_persona_invariants(character=character, persona=persona, facts=facts)
        end_note = self._truncate(
            f"Character invariants:\n{persona_invariants}",
            150,
            keep="head",
        )
        sections.append(PromptSection(name="end_note", content=end_note))

        messages = [{"role": "system", "content": section.content} for section in sections if section.content]
        messages.extend(message.as_chat_message() for message in self._select_recent_messages(recent_messages))
        messages.append({"role": "user", "content": user_input})

        return PromptBundle(
            messages=messages,
            sections=sections,
            active_lore_entries=lore_entries,
            retrieved_facts=facts,
            retrieved_episodes=episodes,
            summary=summary,
            persona_invariants=persona_invariants,
        )

    def build_persona_invariants(
        self,
        *,
        character: CharacterProfile,
        persona: UserPersona,
        facts: list[MemoryFact],
    ) -> str:
        invariants: list[str] = [f"You are {character.name}."]
        if character.speaking_style:
            invariants.append(f"Speaking style: {character.speaking_style}.")
        if character.boundaries:
            invariants.append(f"Do not break these boundaries: {'; '.join(character.boundaries[:3])}.")
        relationship_facts = [
            fact.content
            for fact in facts
            if fact.fact_type == "relationship_state" or fact.pinned
        ]
        if persona.relationship_notes:
            relationship_facts.insert(0, persona.relationship_notes)
        if relationship_facts:
            invariants.append(f"Preserve relationship continuity: {'; '.join(relationship_facts[:3])}.")
        return " ".join(part.strip() for part in invariants if part.strip())

    def detect_persona_conflicts(self, reply: str, character: CharacterProfile) -> list[str]:
        lower_reply = reply.lower()
        conflicts: list[str] = []
        if any(phrase in lower_reply for phrase in GENERIC_AI_PHRASES):
            conflicts.append("The draft referred to itself as a generic AI instead of the active character.")
        identity_match = re.search(r"\b(i am|i'm)\s+([a-z0-9\- ]+)", lower_reply)
        if identity_match and character.name:
            stated_identity = identity_match.group(2).strip(" .,!?:;")
            if stated_identity and character.name.lower() not in stated_identity and "your" not in stated_identity:
                conflicts.append(
                    f"The draft identified itself as '{stated_identity}' instead of '{character.name}'."
                )
        return conflicts

    def _format_persona(self, persona: UserPersona) -> str:
        lines = ["User persona:"]
        lines.append(f"Name: {persona.name}")
        if persona.profile:
            lines.append(f"Profile: {persona.profile}")
        if persona.relationship_notes:
            lines.append(f"Relationship notes: {persona.relationship_notes}")
        if persona.preferences:
            lines.append(f"Preferences: {'; '.join(persona.preferences)}")
        if persona.tone_preferences:
            lines.append(f"Preferred tone: {persona.tone_preferences}")
        return "\n".join(lines)

    def _format_character(self, character: CharacterProfile) -> str:
        lines = ["Character core card:"]
        lines.append(f"Name: {character.name}")
        if character.description:
            lines.append(f"Description: {character.description}")
        if character.personality:
            lines.append(f"Personality: {character.personality}")
        if character.scenario:
            lines.append(f"Scenario: {character.scenario}")
        if character.speaking_style:
            lines.append(f"Speaking style: {character.speaking_style}")
        if character.example_dialogues:
            lines.append("Example dialogue:")
            lines.extend(f"- {line}" for line in character.example_dialogues[:4])
        if character.boundaries:
            lines.append(f"Boundaries: {'; '.join(character.boundaries[:4])}")
        return "\n".join(lines)

    def _format_lore_entry(self, entry: LoreEntry) -> str:
        tags = f" [{', '.join(entry.tags)}]" if entry.tags else ""
        return f"- {entry.title}{tags}: {entry.content}"

    def _format_memory_items(
        self,
        *,
        facts: list[MemoryFact],
        episodes: list[MemoryEpisode],
    ) -> list[str]:
        lines: list[str] = []
        for fact in facts:
            prefix = "Pinned fact" if fact.pinned else fact.fact_type.replace("_", " ")
            lines.append(f"- {prefix}: {fact.content}")
        for episode in episodes:
            lines.append(f"- Episode memory: {episode.content}")
        return lines

    def _truncate_items(self, items: list[str], max_tokens: int) -> str:
        accepted: list[str] = []
        spent = 0
        for item in items:
            item_tokens = estimate_tokens(item)
            if accepted and spent + item_tokens > max_tokens:
                break
            if not accepted and item_tokens > max_tokens:
                accepted.append(self._truncate(item, max_tokens, keep="head"))
                break
            accepted.append(item)
            spent += item_tokens
        return "\n".join(accepted).strip()

    def _select_recent_messages(self, recent_messages: list[MessageRecord]) -> list[MessageRecord]:
        selected: list[MessageRecord] = []
        spent = 0
        for message in reversed(recent_messages):
            tokens = estimate_tokens(message.content)
            if selected and spent + tokens > self.raw_budget:
                break
            selected.append(message)
            spent += tokens
        return list(reversed(selected))

    def _truncate(self, text: str, max_tokens: int, *, keep: str) -> str:
        if max_tokens <= 0:
            return ""
        if estimate_tokens(text) <= max_tokens:
            return text
        max_chars = max_tokens * 4
        if keep == "tail":
            return text[-max_chars:].lstrip()
        return text[:max_chars].rstrip()
