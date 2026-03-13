from __future__ import annotations

import argparse
import os
from pathlib import Path
import sys
from collections.abc import Callable, Mapping, Sequence
from typing import Any

try:
    from openai import OpenAI
except ImportError:  # pragma: no cover - exercised via integration, not unit tests.
    OpenAI = None  # type: ignore[assignment]

from .embeddings import build_embedding_backend
from .llm import OpenAIChatBackend
from .memory import MemoryPipeline
from .prompting import PromptBuilder
from .service import ChatService, SessionContext
from .storage import SQLiteStore


DEEPSEEK_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-chat"
DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant."
DEFAULT_DB_PATH = ".realchat/realchat.db"
EXIT_COMMANDS = frozenset({"/exit", "/quit"})

InputFunc = Callable[[str], str]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Chat with DeepSeek from your terminal.")
    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
        help=f"DeepSeek chat model to use. Defaults to {DEFAULT_MODEL}.",
    )
    parser.add_argument(
        "--system",
        default=DEFAULT_SYSTEM_PROMPT,
        help="Base system prompt for the assistant.",
    )
    parser.add_argument(
        "--character",
        help="Path to a character JSON file.",
    )
    parser.add_argument(
        "--persona",
        help="Path to a user persona JSON file.",
    )
    parser.add_argument(
        "--session",
        help="Resume a specific session ID.",
    )
    parser.add_argument(
        "--db",
        default=None,
        help="Path to the SQLite database. Defaults to REALCHAT_DB_PATH or .realchat/realchat.db.",
    )
    return parser


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    return build_parser().parse_args(argv)


def create_client(api_key: str, *, base_url: str = DEEPSEEK_BASE_URL) -> Any:
    if OpenAI is None:
        raise RuntimeError(
            "The 'openai' package is not installed. Run `uv sync --dev` or install dependencies first."
        )
    return OpenAI(api_key=api_key, base_url=base_url)


def write_line(stream: Any, text: str = "") -> None:
    stream.write(f"{text}\n")
    stream.flush()


def run_repl(
    service: ChatService,
    context: SessionContext,
    input_func: InputFunc = input,
    output_stream: Any = sys.stdout,
) -> int:
    write_line(output_stream, "RealChat CLI")
    write_line(
        output_stream,
        f"Character: {context.character.name} | Session: {context.session.id}",
    )
    write_line(
        output_stream,
        "Commands: /clear /summary /facts /memory /pin /lore /new-session /exit",
    )

    while True:
        try:
            raw_input = input_func("You> ")
        except EOFError:
            write_line(output_stream, "")
            write_line(output_stream, "Bye.")
            return 0
        except KeyboardInterrupt:
            write_line(output_stream, "")
            write_line(output_stream, "Bye.")
            return 0

        user_input = raw_input.strip()
        if not user_input:
            continue

        if user_input.lower() in EXIT_COMMANDS:
            write_line(output_stream, "Bye.")
            return 0

        if user_input.startswith("/"):
            message = service.handle_command(context, user_input)
            if message is None:
                write_line(output_stream, f"Unknown command: {user_input}")
                continue
            write_line(output_stream, message)
            continue

        try:
            reply = service.generate_reply(context, user_input)
        except Exception as exc:  # noqa: BLE001 - surface SDK errors without crashing the REPL.
            message = str(exc).strip() or exc.__class__.__name__
            write_line(output_stream, f"Request failed: {message}")
            continue

        write_line(output_stream, f"{context.character.name}> {reply}")


def main(
    argv: Sequence[str] | None = None,
    environ: Mapping[str, str] | None = None,
    input_func: InputFunc = input,
    output_stream: Any = sys.stdout,
    error_stream: Any = sys.stderr,
) -> int:
    args = parse_args(argv)
    env = environ if environ is not None else os.environ
    api_key = env.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        write_line(error_stream, "Missing DEEPSEEK_API_KEY. Export it before running the CLI.")
        return 1

    base_url = env.get("DEEPSEEK_BASE_URL", "").strip() or DEEPSEEK_BASE_URL
    db_path = args.db or env.get("REALCHAT_DB_PATH", DEFAULT_DB_PATH)
    try:
        client = create_client(api_key, base_url=base_url)
    except Exception as exc:  # noqa: BLE001 - dependency/config failures should be user-visible.
        message = str(exc).strip() or exc.__class__.__name__
        write_line(error_stream, f"Failed to initialize DeepSeek client: {message}")
        return 1

    store = SQLiteStore(Path(db_path))
    try:
        prompt_builder = PromptBuilder()
        chat_backend = OpenAIChatBackend(client)
        memory_pipeline = MemoryPipeline(
            store=store,
            chat_backend=chat_backend,
            embedding_backend=build_embedding_backend(env),
            prompt_builder=prompt_builder,
        )
        service = ChatService(
            store=store,
            chat_backend=chat_backend,
            prompt_builder=prompt_builder,
            memory_pipeline=memory_pipeline,
        )
        context = service.load_context(
            model=args.model,
            base_system_prompt=args.system,
            character_path=args.character,
            persona_path=args.persona,
            session_id=args.session,
        )
        return run_repl(
            service=service,
            context=context,
            input_func=input_func,
            output_stream=output_stream,
        )
    except Exception as exc:  # noqa: BLE001 - input/config errors should be visible to the caller.
        message = str(exc).strip() or exc.__class__.__name__
        write_line(error_stream, f"Failed to initialize chat session: {message}")
        return 1
    finally:
        store.close()
