"""Similarity metrics — the single registry for the whole stack.

Previously the metric functions + names lived in ``search_similarity.METRICS``
while ``vector_store`` kept a separate ``_METRIC_TO_MILVUS`` table with the same
names spelled the same way but maintained independently. Both now derive from
this one module, so a metric is defined exactly once.

All distance metrics are negated so that, for every metric, *higher = more
similar* — the convention the rest of the pipeline relies on.
"""
from __future__ import annotations

import numpy as np


def cosine_similarity(query_vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Cosine similarity between query and every row in matrix. Range [-1, 1]."""
    query_norm = query_vec / (np.linalg.norm(query_vec) + 1e-10)
    matrix_norms = matrix / (np.linalg.norm(matrix, axis=1, keepdims=True) + 1e-10)
    return matrix_norms @ query_norm


def dot_product(query_vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Raw dot product similarity. Higher is more similar."""
    return matrix @ query_vec


def euclidean_distance(query_vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Euclidean (L2) distance, negated so higher = more similar."""
    diff = matrix - query_vec
    return -np.sqrt((diff ** 2).sum(axis=1))


def manhattan_distance(query_vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """Manhattan (L1) distance, negated so higher = more similar."""
    return -np.abs(matrix - query_vec).sum(axis=1)


METRICS = {
    "Cosine Similarity": cosine_similarity,
    "Dot Product":       dot_product,
    "Euclidean (L2)":    euclidean_distance,
    "Manhattan (L1)":    manhattan_distance,
}

# metric name -> Milvus metric type. ``None`` means Milvus has no native index
# for it (Manhattan/L1), so the milvus backend serves that metric via an exact
# numpy fallback instead.
MILVUS_METRIC_TYPE = {
    "Cosine Similarity": "COSINE",
    "Dot Product":       "IP",
    "Euclidean (L2)":    "L2",
    "Manhattan (L1)":    None,
}


def milvus_metric_for(name: str) -> str | None:
    """Milvus metric type for a metric name, or None if unsupported natively."""
    return MILVUS_METRIC_TYPE.get(name)
