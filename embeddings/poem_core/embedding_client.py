"""The one OpenAI-compatible embedding client for the whole stack.

Previously an ``OpenAI(...)`` client was constructed (with the same base_url /
model env handling) in ``sample_embeddings``, ``generate_embeddings`` and
``search_similarity``, and primed again in ``mcp_server``. This module owns that
construction once, lazily (so importing it makes no network call), and adds a
request timeout + bounded retries for robustness — successful-path results are
unchanged.
"""
from __future__ import annotations

import numpy as np
from openai import OpenAI

from . import config

_client: OpenAI | None = None


def get_client() -> OpenAI:
    """Return the shared OpenAI-compatible client, built on first use."""
    global _client
    if _client is None:
        _client = OpenAI(
            base_url=config.EMBED_BASE_URL,
            api_key=config.EMBED_API_KEY,
            timeout=config.EMBED_TIMEOUT,
            max_retries=config.EMBED_MAX_RETRIES,
        )
    return _client


def embed_texts(texts: list[str]) -> list[np.ndarray]:
    """Embed a list of texts in a single request; returns float32 vectors."""
    response = get_client().embeddings.create(model=config.EMBED_MODEL, input=list(texts))
    return [np.array(d.embedding, dtype=np.float32) for d in response.data]


def embed_query(query: str) -> np.ndarray:
    """Return a 1D float32 embedding for a single query string."""
    response = get_client().embeddings.create(model=config.EMBED_MODEL, input=[query])
    return np.array(response.data[0].embedding, dtype=np.float32)
