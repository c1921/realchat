from __future__ import annotations

import argparse
import os
import sys
from collections.abc import Callable, Mapping, Sequence
from typing import Any

try:
    from openai import OpenAI
except ImportError:  # pragma: no cover - exercised via integration, not unit tests.
    OpenAI = None  # type: ignore[assignment]


DEEPSEEK_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-chat"
DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant."
EXIT_COMMANDS = frozenset({"/exit", "/quit"})

ChatMessage = dict[str, str]
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
        help="Optional system prompt for the assistant.",
    )
    return parser


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    return build_parser().parse_args(argv)


def initial_messages(system_prompt: str) -> list[ChatMessage]:
    return [{"role": "system", "content": system_prompt}]


def reset_history(messages: list[ChatMessage], system_prompt: str) -> None:
    messages[:] = initial_messages(system_prompt)


def create_client(api_key: str) -> Any:
    if OpenAI is None:
        raise RuntimeError(
            "The 'openai' package is not installed. Run `uv sync --dev` or install dependencies first."
        )

    return OpenAI(api_key=api_key, base_url=DEEPSEEK_BASE_URL)


def extract_response_text(response: Any) -> str:
    choices = getattr(response, "choices", None) or []
    if not choices:
        raise RuntimeError("DeepSeek returned no choices.")

    message = getattr(choices[0], "message", None)
    content = getattr(message, "content", "")

    if isinstance(content, str):
        text = content.strip()
    elif isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, dict):
                if item.get("type") == "text":
                    parts.append(str(item.get("text", "")))
                continue

            if getattr(item, "type", None) == "text":
                parts.append(str(getattr(item, "text", "")))

        text = "".join(parts).strip()
    else:
        text = ""

    if not text:
        raise RuntimeError("DeepSeek returned an empty response.")

    return text


def request_assistant_reply(client: Any, messages: list[ChatMessage], model: str) -> str:
    response = client.chat.completions.create(
        model=model,
        messages=messages,
        stream=False,
    )
    return extract_response_text(response)


def write_line(stream: Any, text: str = "") -> None:
    stream.write(f"{text}\n")
    stream.flush()


def run_repl(
    client: Any,
    model: str,
    system_prompt: str,
    input_func: InputFunc = input,
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

        command = user_input.lower()
        if command in EXIT_COMMANDS:
            write_line(output_stream, "Bye.")
            return 0

        if command == "/clear":
            reset_history(history, system_prompt)
            write_line(output_stream, "Context cleared.")
            continue

        history.append({"role": "user", "content": user_input})

        try:
            reply = request_assistant_reply(client, history, model)
        except Exception as exc:  # noqa: BLE001 - surface SDK errors without crashing the REPL.
            history.pop()
            message = str(exc).strip() or exc.__class__.__name__
            write_line(output_stream, f"Request failed: {message}")
            continue

        history.append({"role": "assistant", "content": reply})
        write_line(output_stream, f"AI> {reply}")


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
        write_line(
            error_stream,
            "Missing DEEPSEEK_API_KEY. Export it before running the CLI.",
        )
        return 1

    try:
        client = create_client(api_key)
    except Exception as exc:  # noqa: BLE001 - dependency/config failures should be user-visible.
        message = str(exc).strip() or exc.__class__.__name__
        write_line(error_stream, f"Failed to initialize DeepSeek client: {message}")
        return 1

    return run_repl(
        client=client,
        model=args.model,
        system_prompt=args.system,
        input_func=input_func,
        output_stream=output_stream,
    )


if __name__ == "__main__":
    raise SystemExit(main())
