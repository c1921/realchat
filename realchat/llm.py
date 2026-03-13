from __future__ import annotations

from typing import Any, Protocol


class ChatBackend(Protocol):
    def complete(
        self,
        messages: list[dict[str, str]],
        model: str,
        *,
        temperature: float = 0.7,
    ) -> str:
        ...


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


class OpenAIChatBackend:
    def __init__(self, client: Any) -> None:
        self.client = client

    def complete(
        self,
        messages: list[dict[str, str]],
        model: str,
        *,
        temperature: float = 0.7,
    ) -> str:
        response = self.client.chat.completions.create(
            model=model,
            messages=messages,
            temperature=temperature,
            stream=False,
        )
        return extract_response_text(response)
