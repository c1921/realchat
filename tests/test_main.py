from __future__ import annotations

from io import StringIO
from pathlib import Path
from types import SimpleNamespace
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import main


class FakeCompletions:
    def __init__(self, response_text: str = "hello", error: Exception | None = None) -> None:
        self.calls: list[dict[str, object]] = []
        self.response_text = response_text
        self.error = error

    def create(self, **kwargs: object) -> object:
        self.calls.append(kwargs)
        if self.error is not None:
            raise self.error

        message = SimpleNamespace(content=self.response_text)
        choice = SimpleNamespace(message=message)
        return SimpleNamespace(choices=[choice])


def make_client(completions: FakeCompletions) -> object:
    return SimpleNamespace(chat=SimpleNamespace(completions=completions))


def make_input(*responses: str, eof: bool = True):
    items = iter(responses)

    def _input(_: str) -> str:
        try:
            return next(items)
        except StopIteration:
            if eof:
                raise EOFError
            raise AssertionError("No more scripted input.")

    return _input


def test_parse_args_defaults() -> None:
    args = main.parse_args([])

    assert args.model == main.DEFAULT_MODEL
    assert args.system == main.DEFAULT_SYSTEM_PROMPT


def test_parse_args_overrides() -> None:
    args = main.parse_args(["--model", "deepseek-reasoner", "--system", "Be terse"])

    assert args.model == "deepseek-reasoner"
    assert args.system == "Be terse"


def test_parse_args_unknown_flag_raises() -> None:
    with pytest.raises(SystemExit):
        main.parse_args(["--unknown"])


def test_request_assistant_reply_uses_model_and_messages() -> None:
    completions = FakeCompletions(response_text="world")
    client = make_client(completions)
    messages = main.initial_messages("sys")
    messages.append({"role": "user", "content": "hello"})

    reply = main.request_assistant_reply(client, messages, "deepseek-chat")

    assert reply == "world"
    assert completions.calls == [
        {
            "model": "deepseek-chat",
            "messages": messages,
            "stream": False,
        }
    ]


def test_run_repl_ignores_empty_input_and_appends_history() -> None:
    completions = FakeCompletions(response_text="world")
    client = make_client(completions)
    history = main.initial_messages("sys")
    output = StringIO()

    code = main.run_repl(
        client=client,
        model="deepseek-chat",
        system_prompt="sys",
        input_func=make_input("   ", "hello", "/quit"),
        output_stream=output,
        messages=history,
    )

    assert code == 0
    assert history == [
        {"role": "system", "content": "sys"},
        {"role": "user", "content": "hello"},
        {"role": "assistant", "content": "world"},
    ]
    assert "AI> world" in output.getvalue()


def test_run_repl_clear_resets_history() -> None:
    completions = FakeCompletions(response_text="unused")
    client = make_client(completions)
    history = [
        {"role": "system", "content": "sys"},
        {"role": "user", "content": "old"},
        {"role": "assistant", "content": "old reply"},
    ]
    output = StringIO()

    code = main.run_repl(
        client=client,
        model="deepseek-chat",
        system_prompt="sys",
        input_func=make_input("/clear", "/quit"),
        output_stream=output,
        messages=history,
    )

    assert code == 0
    assert history == [{"role": "system", "content": "sys"}]
    assert "Context cleared." in output.getvalue()
    assert completions.calls == []


def test_run_repl_handles_request_error_without_kept_user_message() -> None:
    completions = FakeCompletions(error=RuntimeError("boom"))
    client = make_client(completions)
    history = main.initial_messages("sys")
    output = StringIO()

    code = main.run_repl(
        client=client,
        model="deepseek-chat",
        system_prompt="sys",
        input_func=make_input("hello", "/quit"),
        output_stream=output,
        messages=history,
    )

    assert code == 0
    assert history == [{"role": "system", "content": "sys"}]
    assert "Request failed: boom" in output.getvalue()


def test_run_repl_handles_eof_gracefully() -> None:
    completions = FakeCompletions()
    client = make_client(completions)
    output = StringIO()

    code = main.run_repl(
        client=client,
        model="deepseek-chat",
        system_prompt="sys",
        input_func=make_input(),
        output_stream=output,
    )

    assert code == 0
    assert "Bye." in output.getvalue()


def test_main_requires_api_key() -> None:
    stdout = StringIO()
    stderr = StringIO()

    code = main.main(
        argv=[],
        environ={},
        input_func=make_input("/quit"),
        output_stream=stdout,
        error_stream=stderr,
    )

    assert code == 1
    assert "Missing DEEPSEEK_API_KEY" in stderr.getvalue()
