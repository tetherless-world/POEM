#!/usr/bin/env python3
"""Semantic similarity search over stored POEM embeddings.

Given a query sentence, embeds it and compares it against all stored
paragraph embeddings using multiple similarity metrics.

Usage:
    # Interactive mode
    python embeddings/search_similarity.py

    # Single query from command line
    python embeddings/search_similarity.py "instruments that measure anxiety in children"

    # Control number of results returned per metric
    python embeddings/search_similarity.py "caregiver therapy attendance" --top-k 10
"""

import os
import sys
import argparse
import glob

import numpy as np
from openai import OpenAI

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
_HERE = os.path.dirname(os.path.abspath(__file__))
SECTIONS = ["instruments", "scales", "collections"]
DEFAULT_TOP_K = 5

client = OpenAI(
    base_url="http://idea-llm-02.idea.rpi.edu:1234/v1",
    api_key="not-needed"
)

# ---------------------------------------------------------------------------
# Load all stored embeddings and texts
# ---------------------------------------------------------------------------

def load_embeddings() -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Load all paragraph embeddings and their source texts from disk.

    Returns:
        embeddings : float32 array of shape (N, dim)
        texts      : object array of shape (N,) with source text strings
        sections   : object array of shape (N,) with section name per paragraph
    """
    all_embeddings = []
    all_texts = []
    all_sections = []

    for section in SECTIONS:
        section_dir = os.path.join(_HERE, section)
        if not os.path.isdir(section_dir):
            print(f"  Warning: section folder not found: {section_dir}")
            print(f"  Run generate_embeddings.py first to create embeddings.")
            continue

        texts_path = os.path.join(section_dir, "texts.npy")
        if not os.path.exists(texts_path):
            print(f"  Warning: texts.npy missing in {section_dir}")
            continue

        texts = np.load(texts_path, allow_pickle=True)

        # Load paragraph_0.npy, paragraph_1.npy, ... in order
        para_files = sorted(
            glob.glob(os.path.join(section_dir, "paragraph_*.npy")),
            key=lambda p: int(os.path.splitext(os.path.basename(p))[0].split("_")[1])
        )

        if not para_files:
            print(f"  Warning: no paragraph files found in {section_dir}")
            continue

        section_embeddings = np.stack([np.load(p) for p in para_files])  # (N, dim)

        all_embeddings.append(section_embeddings)
        all_texts.append(texts[:len(para_files)])
        all_sections.append(np.array([section] * len(para_files), dtype=object))

        print(f"  Loaded {len(para_files)} paragraphs from '{section}'")

    if not all_embeddings:
        print("No embeddings found. Run generate_embeddings.py first.")
        sys.exit(1)

    embeddings = np.concatenate(all_embeddings, axis=0)   # (N_total, dim)
    texts      = np.concatenate(all_texts, axis=0)        # (N_total,)
    sections   = np.concatenate(all_sections, axis=0)     # (N_total,)
    return embeddings, texts, sections


# ---------------------------------------------------------------------------
# Embed the query sentence
# ---------------------------------------------------------------------------

def embed_query(query: str) -> np.ndarray:
    """Return a 1D float32 embedding for the query string."""
    response = client.embeddings.create(
        model="qwen3-embedding:latest",
        input=[query]
    )
    return np.array(response.data[0].embedding, dtype=np.float32)


# ---------------------------------------------------------------------------
# Similarity metrics
# ---------------------------------------------------------------------------

def cosine_similarity(query_vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Cosine similarity between query and every row in matrix.
    Range: [-1, 1]. Higher is more similar.
    """
    query_norm = query_vec / (np.linalg.norm(query_vec) + 1e-10)
    matrix_norms = matrix / (np.linalg.norm(matrix, axis=1, keepdims=True) + 1e-10)
    return matrix_norms @ query_norm


def dot_product(query_vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Raw dot product similarity. Higher is more similar."""
    return matrix @ query_vec


def euclidean_distance(query_vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Euclidean (L2) distance. Lower is more similar.
    Returned as negative so higher = more similar (consistent with other metrics).
    """
    diff = matrix - query_vec
    return -np.sqrt((diff ** 2).sum(axis=1))


def manhattan_distance(query_vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Manhattan (L1) distance. Lower is more similar.
    Returned as negative so higher = more similar.
    """
    return -np.abs(matrix - query_vec).sum(axis=1)


METRICS = {
    "Cosine Similarity":   cosine_similarity,
    "Dot Product":         dot_product,
    "Euclidean (L2)":      euclidean_distance,
    "Manhattan (L1)":      manhattan_distance,
}


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

def run_search(query: str, top_k: int, embeddings: np.ndarray, texts: np.ndarray, sections: np.ndarray):
    print(f'\nQuery: "{query}"')
    print("Embedding query...")
    query_vec = embed_query(query)

    for metric_name, metric_fn in METRICS.items():
        scores = metric_fn(query_vec, embeddings)
        print_results(metric_name, scores, texts, sections, top_k)


def main():
    parser = argparse.ArgumentParser(description="Search POEM embeddings with multiple similarity metrics.")
    parser.add_argument("query", nargs="?", default=None, help="Query sentence (omit for interactive mode)")
    parser.add_argument("--top-k", type=int, default=DEFAULT_TOP_K, help=f"Number of results per metric (default: {DEFAULT_TOP_K})")
    args = parser.parse_args()

    print("Loading embeddings...")
    embeddings, texts, sections = load_embeddings()
    print(f"Total paragraphs loaded: {len(texts)}")

    if args.query:
        run_search(args.query, args.top_k, embeddings, texts, sections)
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
            run_search(query, args.top_k, embeddings, texts, sections)


if __name__ == "__main__":
    main()
