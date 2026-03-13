from __future__ import annotations

import sys
from typing import Any

from realchat import (
    DEEPSEEK_BASE_URL,
    DEFAULT_MODEL,
    DEFAULT_SYSTEM_PROMPT,
    build_parser,
    create_client,
    extract_response_text,
    main as app_main,
    parse_args,
)
from realchat.cli import EXIT_COMMANDS, write_line
from realchat.llm import OpenAIChatBackend


ChatMessage = dict[str, str]


def initial_messages(system_prompt: str) -> list[ChatMessage]:
    return [{"role": "system", "content": system_prompt}]


def reset_history(messages: list[ChatMessage], system_prompt: str) -> None:
    messages[:] = initial_messages(system_prompt)


def request_assistant_reply(client: Any, messages: list[ChatMessage], model: str) -> str:
    return OpenAIChatBackend(client).complete(messages, model, temperature=0.7)


def run_repl(
    client: Any,
    model: str,
    system_prompt: str,
    input_func=input,
    output_stream: Any = sys.stdout,
    messages: list[ChatMessage] | None = None,
) -> int:
    history = messages if messages is not None else initial_messages(system_prompt)

    write_line(output_stream, "RealChat CLI")
    write_line(output_stream, "Use /clear to reset context, /exit or /quit to leave.")

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

        if user_input.lower() == "/clear":
            reset_history(history, system_prompt)
            write_line(output_stream, "Context cleared.")
            continue

        history.append({"role": "user", "content": user_input})
        try:
            reply = request_assistant_reply(client, history, model)
        except Exception as exc:  # noqa: BLE001 - preserve legacy behavior.
            history.pop()
            message = str(exc).strip() or exc.__class__.__name__
            write_line(output_stream, f"Request failed: {message}")
            continue

        history.append({"role": "assistant", "content": reply})
        write_line(output_stream, f"AI> {reply}")


def main(*args: Any, **kwargs: Any) -> int:
    return app_main(*args, **kwargs)


if __name__ == "__main__":
    raise SystemExit(main())
