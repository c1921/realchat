from __future__ import annotations

from io import StringIO
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import main
from realchat.embeddings import HashingEmbeddingBackend
from realchat.memory import MemoryPipeline
from realchat.models import CharacterProfile, MemoryCandidate, UserPersona
from realchat.prompting import PromptBuilder
from realchat.service import ChatService
import realchat.cli as cli
from realchat.storage import SQLiteStore


REPO_ROOT = Path(__file__).resolve().parents[1]
EXAMPLES_DIR = REPO_ROOT / "examples"


class FakeChatBackend:
    def __init__(self, responses: list[object] | None = None) -> None:
        self.responses = list(responses or [])
        self.calls: list[dict[str, object]] = []

    def complete(
        self,
        messages: list[dict[str, str]],
        model: str,
        *,
        temperature: float = 0.7,
    ) -> str:
        self.calls.append(
            {
                "messages": messages,
                "model": model,
                "temperature": temperature,
            }
        )
        if not self.responses:
            return "ok"
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return str(response)


def character_payload() -> dict[str, object]:
    return json.loads((EXAMPLES_DIR / "alice.json").read_text(encoding="utf-8"))


def persona_payload() -> dict[str, object]:
    return json.loads((EXAMPLES_DIR / "jun.json").read_text(encoding="utf-8"))


def build_service(tmp_path: Path, responses: list[object] | None = None) -> tuple[ChatService, SQLiteStore, FakeChatBackend]:
    store = SQLiteStore(tmp_path / "realchat.db")
    backend = FakeChatBackend(responses)
    prompt_builder = PromptBuilder(total_context_tokens=2_000)
    memory_pipeline = MemoryPipeline(
        store=store,
        chat_backend=backend,
        embedding_backend=HashingEmbeddingBackend(),
        prompt_builder=prompt_builder,
        total_context_tokens=2_000,
    )
    service = ChatService(
        store=store,
        chat_backend=backend,
        prompt_builder=prompt_builder,
        memory_pipeline=memory_pipeline,
    )
    return service, store, backend


def load_context(service: ChatService, tmp_path: Path):
    character_path = str(EXAMPLES_DIR / "alice.json")
    persona_path = str(EXAMPLES_DIR / "jun.json")
    context = service.load_context(
        model="deepseek-chat",
        base_system_prompt="Stay consistent across sessions.",
        character_path=character_path,
        persona_path=persona_path,
    )
    return context, character_path, persona_path


def test_parse_args_supports_long_memory_flags() -> None:
    args = main.parse_args([])
    assert args.model == main.DEFAULT_MODEL
    assert args.system == main.DEFAULT_SYSTEM_PROMPT
    assert args.character is None
    assert args.persona is None
    assert args.session is None

    custom = main.parse_args(
        [
            "--model",
            "deepseek-reasoner",
            "--system",
            "Keep memory stable",
            "--character",
            "char.json",
            "--persona",
            "persona.json",
            "--session",
            "session-123",
            "--db",
            "memory.db",
        ]
    )
    assert custom.model == "deepseek-reasoner"
    assert custom.character == "char.json"
    assert custom.persona == "persona.json"
    assert custom.session == "session-123"
    assert custom.db == "memory.db"


def test_prompt_order_includes_summary_lore_memory_and_end_note(tmp_path: Path) -> None:
    service, store, _ = build_service(tmp_path)
    context, _, _ = load_context(service, tmp_path)
    store.save_summary(context.session.id, "Alice and Jun already share a quiet nightly routine.", 1)
    store.upsert_fact(
        context.session.id,
        context.character.id,
        MemoryCandidate(
            content="Jun prefers black tea in rainy weather.",
            importance=0.9,
            confidence=0.8,
            kind="stable_fact",
        ),
        turn_index=1,
        pinned=True,
    )
    episode = store.save_episode(
        context.session.id,
        context.character.id,
        MemoryCandidate(
            content="They once watched the harbor fireworks from the bookstore doorway.",
            importance=0.8,
            confidence=0.7,
            kind="episode",
        ),
        turn_index=1,
    )
    store.save_embedding("episode", episode.id, HashingEmbeddingBackend().embed_text(episode.content))
    store.append_message(context.session.id, 1, "user", "We stayed late in the bookstore last week.")
    store.append_message(context.session.id, 1, "assistant", "I still remember the harbor lights.")
    context.session.turn_count = 1
    store.save_session(context.session)

    bundle = service.build_prompt_bundle(context, "今夜书店外的雨声更轻了。")

    assert [section.name for section in bundle.sections] == [
        "base_system",
        "persona",
        "character_core",
        "summary",
        "lore",
        "memory",
        "end_note",
    ]
    assert bundle.active_lore_entries[0].title == "雨夜书店"
    assert bundle.messages[-1] == {
        "role": "user",
        "content": "今夜书店外的雨声更轻了。",
    }


def test_resume_latest_session_and_keep_memories_across_reload(tmp_path: Path) -> None:
    responses = [
        "Alice leaned closer and remembered the tea order.",
        json.dumps(
            {
                "stable_facts": ["Jun likes jasmine tea."],
                "relationship_state": ["Alice trusts Jun with personal stories."],
                "episodes": ["They planned to visit the pier together next week."],
            }
        ),
    ]
    service, store, _ = build_service(tmp_path, responses)
    context, character_path, persona_path = load_context(service, tmp_path)

    reply = service.generate_reply(context, "Please remember that I love jasmine tea.")

    assert "Alice leaned closer" in reply
    original_session_id = context.session.id
    assert any("jasmine tea" in fact.content.lower() for fact in store.list_facts(context.session.id))

    reload_service, reload_store, _ = build_service(tmp_path)
    reloaded = reload_service.load_context(
        model="deepseek-chat",
        base_system_prompt="Stay consistent across sessions.",
        character_path=character_path,
        persona_path=persona_path,
    )

    assert reloaded.session.id == original_session_id
    assert any("jasmine tea" in fact.content.lower() for fact in reload_store.list_facts(reloaded.session.id))
    store.close()
    reload_store.close()


def test_clear_resets_recent_window_but_keeps_pinned_memory(tmp_path: Path) -> None:
    responses = [
        "Alice nodded and filed it away.",
        json.dumps({"stable_facts": [], "relationship_state": [], "episodes": ["They discussed favorite tea."]}),
    ]
    service, store, _ = build_service(tmp_path, responses)
    context, _, _ = load_context(service, tmp_path)
    service.generate_reply(context, "Remember that my favorite tea is oolong.")
    result = service.handle_command(context, "/pin Jun's favorite tea is oolong.")
    assert result == "Pinned fact stored."

    cleared = service.handle_command(context, "/clear")
    assert cleared == "Recent context cleared. Long-term memory preserved."

    bundle = service.build_prompt_bundle(context, "What should we drink tonight?")
    system_text = "\n".join(section.content for section in bundle.sections)
    non_system_messages = [message for message in bundle.messages if message["role"] != "system"]

    assert "Jun's favorite tea is oolong." in system_text
    assert all("favorite tea" not in message["content"] for message in non_system_messages[:-1])


def test_summary_refresh_after_twelfth_turn_updates_anchor(tmp_path: Path) -> None:
    service, store, _ = build_service(
        tmp_path,
        [
            "Alice answered in a steady voice.",
            json.dumps({"stable_facts": [], "relationship_state": [], "episodes": ["A new late-night exchange happened."]}),
            "Alice and Jun have now completed twelve nights of quiet conversation.",
        ],
    )
    context, _, _ = load_context(service, tmp_path)
    for turn in range(1, 12):
        store.append_message(context.session.id, turn, "user", f"user-{turn}")
        store.append_message(context.session.id, turn, "assistant", f"assistant-{turn}")
    context.session.turn_count = 11
    context.session.latest_summary_turn = 0
    store.save_session(context.session)

    service.generate_reply(context, "This is the twelfth turn.")
    updated = store.get_session(context.session.id)
    summary = store.get_latest_summary(context.session.id)

    assert updated is not None
    assert updated.latest_summary_turn == 12
    assert updated.raw_window_anchor_turn == 8
    assert summary is not None
    assert "twelve nights" in summary.content


def test_episode_retrieval_prefers_semantic_match(tmp_path: Path) -> None:
    service, store, _ = build_service(tmp_path)
    context, _, _ = load_context(service, tmp_path)
    beach = store.save_episode(
        context.session.id,
        context.character.id,
        MemoryCandidate(
            content="They watched fireworks over the harbor and talked about the sea breeze.",
            importance=0.9,
            confidence=0.8,
            kind="episode",
        ),
        turn_index=1,
    )
    logs = store.save_episode(
        context.session.id,
        context.character.id,
        MemoryCandidate(
            content="They debugged server logs and rotated API keys during a deployment.",
            importance=0.7,
            confidence=0.8,
            kind="episode",
        ),
        turn_index=2,
    )
    embedder = HashingEmbeddingBackend()
    store.save_embedding("episode", beach.id, embedder.embed_text(beach.content))
    store.save_embedding("episode", logs.id, embedder.embed_text(logs.content))

    _, episodes = service.memory_pipeline.retrieve_memories(
        session=context.session,
        user_input="Do you remember the fireworks by the sea?",
        recent_messages=[],
        active_lore_entries=[],
    )

    assert episodes
    assert episodes[0].id == beach.id


def test_persona_conflict_triggers_single_retry(tmp_path: Path) -> None:
    service, store, backend = build_service(
        tmp_path,
        [
            "As an AI language model, I cannot roleplay that.",
            "Alice smiled softly and stayed in character.",
            json.dumps({"stable_facts": [], "relationship_state": [], "episodes": ["They recovered from a drifted reply."]}),
        ],
    )
    context, _, _ = load_context(service, tmp_path)

    reply = service.generate_reply(context, "Stay with me in the bookstore.")

    assert reply == "Alice smiled softly and stayed in character."
    assert len(backend.calls) == 3
    assert backend.calls[1]["temperature"] == 0.4
    retry_messages = backend.calls[1]["messages"]
    assert any(
        "drifted away from the active character" in message["content"]
        for message in retry_messages  # type: ignore[union-attr]
        if message["role"] == "system"
    )
    store.close()


def test_main_requires_api_key() -> None:
    stdout = StringIO()
    stderr = StringIO()

    code = main.main(
        argv=[],
        environ={},
        input_func=lambda _: "/quit",
        output_stream=stdout,
        error_stream=stderr,
    )

    assert code == 1
    assert "Missing DEEPSEEK_API_KEY" in stderr.getvalue()


def test_example_files_load_from_repo() -> None:
    character = CharacterProfile.from_json_file(EXAMPLES_DIR / "alice.json")
    persona = UserPersona.from_json_file(EXAMPLES_DIR / "jun.json")

    assert character.id == "char-alice"
    assert character.name == "Alice"
    assert len(character.lorebook) == 3
    assert character.lorebook[0].title == "雨夜书店"
    assert persona.id == "persona-jun"
    assert persona.name == "Jun"
    assert "连续" in persona.relationship_notes


def test_example_lore_activates_on_rainy_bookstore_prompt(tmp_path: Path) -> None:
    service, store, _ = build_service(tmp_path)
    context, _, _ = load_context(service, tmp_path)

    bundle = service.build_prompt_bundle(context, "今夜书店外又下雨了，给我留一盏灯吧。")

    assert bundle.active_lore_entries
    assert bundle.active_lore_entries[0].title == "雨夜书店"
    assert any(section.name == "lore" and "旧纸页" in section.content for section in bundle.sections)
    store.close()


def test_main_can_initialize_with_example_files(monkeypatch, tmp_path: Path) -> None:
    fake_client = object()

    def fake_create_client(api_key: str, *, base_url: str = cli.DEEPSEEK_BASE_URL) -> object:
        assert api_key == "test-key"
        return fake_client

    monkeypatch.setattr(cli, "create_client", fake_create_client)
    stdout = StringIO()
    stderr = StringIO()
    db_path = tmp_path / "realchat.db"

    code = main.main(
        argv=[
            "--character",
            str(EXAMPLES_DIR / "alice.json"),
            "--persona",
            str(EXAMPLES_DIR / "jun.json"),
            "--db",
            str(db_path),
        ],
        environ={"DEEPSEEK_API_KEY": "test-key"},
        input_func=lambda _: "/quit",
        output_stream=stdout,
        error_stream=stderr,
    )

    assert code == 0
    assert "Character: Alice" in stdout.getvalue()
    assert stderr.getvalue() == ""
