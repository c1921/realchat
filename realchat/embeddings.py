from __future__ import annotations

from collections.abc import Mapping
import hashlib
import math
import re
from typing import Protocol

from .models import normalize_memory_key


class EmbeddingBackend(Protocol):
    def embed_text(self, text: str) -> list[float]:
        ...


class HashingEmbeddingBackend:
    def __init__(self, dimensions: int = 128) -> None:
        self.dimensions = dimensions

    def embed_text(self, text: str) -> list[float]:
        vector = [0.0] * self.dimensions
        terms = re.findall(r"[\w\u4e00-\u9fff]+", normalize_memory_key(text))
        if not terms:
            return vector
        for term in terms:
            digest = hashlib.sha256(term.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % self.dimensions
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            weight = 1.0 + (digest[5] / 255.0)
            vector[index] += sign * weight
        return normalize_vector(vector)


class OpenAIEmbeddingBackend:
    def __init__(self, api_key: str, model: str, base_url: str | None = None) -> None:
        try:
            from openai import OpenAI
        except ImportError as exc:  # pragma: no cover - import failure is environment-specific.
            raise RuntimeError("The 'openai' package is required for remote embeddings.") from exc
        kwargs: dict[str, str] = {"api_key": api_key}
        if base_url:
            kwargs["base_url"] = base_url
        self.client = OpenAI(**kwargs)
        self.model = model

    def embed_text(self, text: str) -> list[float]:
        response = self.client.embeddings.create(model=self.model, input=text)
        data = getattr(response, "data", None) or []
        if not data:
            raise RuntimeError("Embedding API returned no vectors.")
        embedding = getattr(data[0], "embedding", None)
        if not isinstance(embedding, list):
            raise RuntimeError("Embedding API returned an invalid vector.")
        return normalize_vector([float(value) for value in embedding])


def build_embedding_backend(environ: Mapping[str, str]) -> EmbeddingBackend:
    api_key = environ.get("EMBEDDING_API_KEY", "").strip()
    model = environ.get("EMBEDDING_MODEL", "").strip()
    base_url = environ.get("EMBEDDING_BASE_URL", "").strip() or None
    if api_key and model:
        return OpenAIEmbeddingBackend(api_key=api_key, model=model, base_url=base_url)
    return HashingEmbeddingBackend()


def normalize_vector(vector: list[float]) -> list[float]:
    norm = math.sqrt(sum(value * value for value in vector))
    if norm <= 0:
        return vector
    return [value / norm for value in vector]


def cosine_similarity(left: list[float], right: list[float]) -> float:
    if not left or not right or len(left) != len(right):
        return 0.0
    return sum(a * b for a, b in zip(left, right, strict=True))
