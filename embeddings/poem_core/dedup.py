"""Deduplicate ranked search hits by entity.

``get_unique_top_results`` used to live in ``evaluate_search`` and was imported
from there by ``mcp_server`` (forcing the MCP server to reach into the Pipeline
folder). It is a generic result-shaping helper with no evaluation-specific
state, so it lives in the core and both callers import it from here.
"""
from __future__ import annotations

import numpy as np

from .entities import extract_entity_name


def get_unique_top_results(
    scores: np.ndarray,
    texts: np.ndarray,
    sections: np.ndarray,
    top_k_search: int,
    top_k_unique: int,
) -> list[dict]:
    """Return up to ``top_k_unique`` results, deduplicated by entity name.

    Walks the ``top_k_search`` highest-scoring results in descending score
    order; the first paragraph seen for each entity is kept and later paragraphs
    from the same entity are skipped. ``raw_rank`` records the pre-dedup position.
    """
    top_indices = np.argsort(scores)[::-1][:top_k_search]
    seen_entities: set[str] = set()
    unique_results: list[dict] = []

    for raw_rank, idx in enumerate(top_indices, start=1):
        entity = extract_entity_name(texts[idx])
        if entity in seen_entities:
            continue
        seen_entities.add(entity)
        preview = texts[idx].replace("\n", " ")
        if len(preview) > 120:
            preview = preview[:117] + "..."
        unique_results.append({
            "unique_rank": len(unique_results) + 1,
            "raw_rank": raw_rank,
            "idx": int(idx),
            "entity": entity,
            "section": sections[idx],
            "score": float(scores[idx]),
            "preview": preview,
        })
        if len(unique_results) >= top_k_unique:
            break

    return unique_results
