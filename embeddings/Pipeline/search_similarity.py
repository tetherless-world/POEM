#!/usr/bin/env python3
"""Semantic similarity search over stored POEM embeddings (CLI).

Thin entry layer: the reusable building blocks — config, similarity metrics, the
corpus loader, the embedding client, and the vector store — live in
``poem_core`` and are re-exported here so the historical import surface keeps
working unchanged:

    from search_similarity import METRICS, SECTIONS, embed_query, load_embeddings
    from search_similarity import cosine_similarity, dot_product, ...

Usage:
    # Interactive mode
    python search_similarity.py

    # Single query from the command line
    python search_similarity.py "instruments that measure anxiety in children"

    # Control number of results returned per metric
    python search_similarity.py "caregiver therapy attendance" --top-k 10
"""
from __future__ import annotations

import os as _os
import sys as _sys

_EMB_ROOT = _os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))
if _EMB_ROOT not in _sys.path:
    _sys.path.insert(0, _EMB_ROOT)

import argparse  # noqa: E402

import numpy as np  # noqa: E402

from poem_core import config  # noqa: E402
from poem_core.metrics import (  # noqa: E402,F401  (re-exported for callers/tests)
    METRICS,
    cosine_similarity,
    dot_product,
    euclidean_distance,
    manhattan_distance,
)
from poem_core.entities import extract_entity_name  # noqa: E402,F401
from poem_core.embedding_client import embed_query  # noqa: E402
from poem_core.corpus import (  # noqa: E402,F401
    SECTIONS,
    discover_sections,
    load_embeddings,
)
from poem_core.vector_store import get_store  # noqa: E402

DEFAULT_TOP_K = 5
EMBEDDINGS_DIR = config.EMBEDDINGS_DIR


# ---------------------------------------------------------------------------
# Display results
# ---------------------------------------------------------------------------

def print_results(
    metric_name: str,
    scores: np.ndarray,
    texts: np.ndarray,
    sections: np.ndarray,
    top_k: int,
):
    top_indices = np.argsort(scores)[::-1][:top_k]
    print(f"\n{'='*70}")
    print(f"  {metric_name}  —  Top {top_k} results")
    print(f"{'='*70}")
    for rank, idx in enumerate(top_indices, start=1):
        score = scores[idx]
        section = sections[idx]
        text_preview = texts[idx].replace("\n", " ")
        if len(text_preview) > 120:
            text_preview = text_preview[:117] + "..."
        print(f"  #{rank:2d}  [{section:12s}]  score={score:+.4f}")
        print(f"       {text_preview}")
    print()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def run_search(query: str, top_k: int, store):
    """Embed the query once and print top-k results for every metric.

    ``store`` is a vector-store backend (numpy or external Milvus). Building it
    once (in ``main``) keeps the external backend from rebuilding per query and
    is what lets interactive mode stay responsive.
    """
    print(f'\nQuery: "{query}"')
    print("Embedding query...")
    query_vec = embed_query(query)

    for metric_name in METRICS:
        scores, txt, sec = store.top_candidates(query_vec, metric_name, None, k=top_k)
        print_results(metric_name, scores, txt, sec, top_k)


def main():
    parser = argparse.ArgumentParser(description="Search POEM embeddings with multiple similarity metrics.")
    parser.add_argument("query", nargs="?", default=None, help="Query sentence (omit for interactive mode)")
    parser.add_argument("--top-k", type=int, default=DEFAULT_TOP_K, help=f"Number of results per metric (default: {DEFAULT_TOP_K})")
    parser.add_argument("--section", choices=SECTIONS, default=None,
                        help="Restrict search to a single section (instruments, scales, or collections)")
    args = parser.parse_args()

    print("Loading embeddings...")
    filter_sections = [args.section] if args.section else None
    embeddings, texts, sections = load_embeddings(filter_sections)
    print(f"Total paragraphs loaded: {len(texts)}")

    store = get_store(embeddings, texts, sections)

    if args.query:
        run_search(args.query, args.top_k, store)
    else:
        print("\nInteractive mode — type a sentence and press Enter. Type 'quit' to exit.\n")
        while True:
            try:
                query = input("Query> ").strip()
            except (EOFError, KeyboardInterrupt):
                print("\nExiting.")
                break
            if not query:
                continue
            if query.lower() in ("quit", "exit", "q"):
                break
            run_search(query, args.top_k, store)


if __name__ == "__main__":
    main()


'''
python embeddings/Pipeline/search_similarity.py
fear of public speaking social phobia subscale
'''