"""Pluggable vector store for POEM search.

Decouples *how vectors are stored and searched* from the rest of the pipeline:

  * ``NumpyVectorStore`` — all vectors in one numpy array, scored by the metric
    functions in ``poem_core.metrics``. Zero extra dependencies. Cosine reuses a
    once-computed row-normalized matrix instead of renormalizing every query.

  * ``MilvusVectorStore`` — an external Milvus server (Standalone, separate
    process). Each supported metric is materialized as its own collection with
    an explicit **FLAT** index, so results are *exact* (not approximate) and
    match the numpy backend. Manhattan (L1), which Milvus has no native metric
    for, is served by an internal exact numpy fallback. The collection is built
    from the in-memory vectors at startup and loaded into memory for search.

``get_store`` selects the backend from ``VECTOR_BACKEND`` (default ``milvus``)
and, if Milvus is unreachable or pymilvus is missing, transparently falls back
to numpy so offline/dev use and tests keep working.

Both backends expose one method:

    top_candidates(query_vec, metric, section, k) -> (scores, texts, sections)

The numpy backend returns the full (optionally section-masked) corpus so the
existing dedup-by-entity in ``poem_core.dedup`` is exact; the milvus backend
returns its top-k. Callers run the same dedup helper over either.
"""
from __future__ import annotations

import os
import sys

import numpy as np

from . import config
from .metrics import METRICS, MILVUS_METRIC_TYPE, milvus_metric_for

# Back-compat: the old module exposed this name (supported metrics only).
_METRIC_TO_MILVUS = {k: v for k, v in MILVUS_METRIC_TYPE.items() if v is not None}


class NumpyVectorStore:
    """In-process numpy store — the default-equivalent, exact for every metric."""

    def __init__(self, embeddings: np.ndarray, texts: np.ndarray, sections: np.ndarray):
        self.emb = embeddings
        self.texts = texts
        self.sections = sections
        self._normed: np.ndarray | None = None  # row-normalized matrix, lazy

    def _normed_matrix(self) -> np.ndarray:
        if self._normed is None:
            self._normed = self.emb / (np.linalg.norm(self.emb, axis=1, keepdims=True) + 1e-10)
        return self._normed

    def top_candidates(self, query_vec, metric, section, k=None):
        # k is unused: numpy scores the whole (optionally section-masked) corpus,
        # preserving the exact ranking the pipeline always produced.
        if section is not None:
            mask = (self.sections == section)
            txt, sec = self.texts[mask], self.sections[mask]
            if metric == "Cosine Similarity":
                normed = self._normed_matrix()[mask]
            else:
                emb = self.emb[mask]
        else:
            txt, sec = self.texts, self.sections
            if metric == "Cosine Similarity":
                normed = self._normed_matrix()
            else:
                emb = self.emb

        if metric == "Cosine Similarity":
            q = query_vec / (np.linalg.norm(query_vec) + 1e-10)
            scores = normed @ q
        else:
            scores = METRICS[metric](query_vec, emb)
        return scores, txt, sec


class MilvusVectorStore:
    """External-Milvus backed store: exact (FLAT) and multi-metric.

    One FLAT collection per supported metric is built lazily and cached; the
    default (Cosine) is warmed at construction so an unreachable server / missing
    pymilvus surfaces immediately and ``get_store`` can fall back to numpy.
    Manhattan (L1) is served from an internal NumpyVectorStore.
    """

    def __init__(
        self,
        embeddings: np.ndarray,
        texts: np.ndarray,
        sections: np.ndarray,
        uri: str | None = None,
        collection: str | None = None,
        token: str | None = None,
        rebuild: bool = False,
        metric: str | None = None,  # accepted for back-compat; no longer fixed
    ):
        from pymilvus import MilvusClient  # lazy: only needed for this backend

        self.emb = embeddings
        self.texts = texts
        self.sections = sections
        self.uri = uri or config.milvus_uri()
        self.base = collection or config.milvus_collection()
        self.rebuild = rebuild
        self._dim = int(embeddings.shape[1])
        self._built: set[str] = set()           # metric types already materialized
        self._numpy = NumpyVectorStore(embeddings, texts, sections)  # L1 + safety

        token = token if token is not None else config.milvus_token()
        self.client = MilvusClient(uri=self.uri, token=token) if token else MilvusClient(uri=self.uri)
        # Probe connectivity now so a down server triggers fallback in get_store.
        self.client.list_collections()
        # Warm the default metric (validates the full build path against the server).
        self._ensure(MILVUS_METRIC_TYPE["Cosine Similarity"])

    def _collection_name(self, metric_type: str) -> str:
        return f"{self.base}_{metric_type.lower()}"

    def _ensure(self, metric_type: str) -> str:
        """Build (or reuse) the FLAT collection for one Milvus metric type."""
        from pymilvus import DataType

        name = self._collection_name(metric_type)
        if metric_type in self._built:
            return name

        if self.client.has_collection(name):
            # Reuse the collection only if it already holds every row. NOTE:
            # get_collection_stats' row_count counts *sealed* segments only, so it
            # reads 0 until a flush — against a server that hasn't flushed (e.g.
            # Zilliz Cloud right after insert) that made this guard rebuild the
            # whole collection on every startup. An exact count(*) query is
            # accurate regardless of flush state. (query requires the collection
            # loaded, so load first; that load is what we want on the reuse path.)
            reuse = False
            if not self.rebuild:
                try:
                    self.client.load_collection(name)
                    res = self.client.query(collection_name=name, filter="id >= 0",
                                            output_fields=["count(*)"])
                    count = int(res[0]["count(*)"]) if res else 0
                    reuse = count == len(self.texts)
                except Exception:
                    reuse = False
            if reuse:
                self._built.add(metric_type)
                return name
            self.client.drop_collection(name)

        # Explicit schema + FLAT index => exact nearest neighbors (parity with numpy).
        schema = self.client.create_schema(auto_id=False, enable_dynamic_field=True)
        schema.add_field("id", DataType.INT64, is_primary=True)
        schema.add_field("vector", DataType.FLOAT_VECTOR, dim=self._dim)

        index_params = self.client.prepare_index_params()
        index_params.add_index(field_name="vector", index_type="FLAT", metric_type=metric_type)

        self.client.create_collection(collection_name=name, schema=schema, index_params=index_params)

        rows = [
            {"id": i, "vector": self.emb[i].tolist(),
             "text": str(self.texts[i]), "section": str(self.sections[i])}
            for i in range(len(self.texts))
        ]
        for i in range(0, len(rows), 1000):
            self.client.insert(name, rows[i:i + 1000])
        self.client.load_collection(name)
        self._built.add(metric_type)
        return name

    def top_candidates(self, query_vec, metric, section, k):
        metric_type = milvus_metric_for(metric)
        if metric_type is None:
            # Manhattan (L1) etc.: Milvus has no native metric — exact numpy.
            return self._numpy.top_candidates(query_vec, metric, section, k)

        name = self._ensure(metric_type)
        flt = f'section == "{section}"' if section else ""
        res = self.client.search(
            collection_name=name,
            data=[query_vec.tolist()],
            limit=int(k),
            filter=flt,
            output_fields=["text", "section"],
            search_params={"metric_type": metric_type},
        )
        hits = res[0]
        # COSINE/IP: higher = more similar. L2: smaller = closer, so negate to
        # keep the pipeline's "higher = better" convention.
        sign = -1.0 if metric_type == "L2" else 1.0
        scores = np.array([sign * h["distance"] for h in hits], dtype=np.float32)
        txt = np.array([h["entity"]["text"] for h in hits], dtype=object)
        sec = np.array([h["entity"]["section"] for h in hits], dtype=object)
        return scores, txt, sec


def get_store(embeddings, texts, sections):
    """Build the configured backend from already-loaded corpus arrays.

    ``VECTOR_BACKEND=milvus`` (default) uses the external Milvus server; if it is
    unreachable or pymilvus is not installed, this logs a warning to stderr and
    falls back to the numpy backend so the system still works offline.

    For a *local* ``MILVUS_URI``, this first runs ``docker_preflight.ensure_milvus_ready()``
    (start Docker Desktop / the compose stack if either is down), so the Milvus
    path self-heals across reboots instead of silently falling back. Set
    ``MILVUS_SKIP_ENSURE=1`` to disable. A remote/cloud URI never triggers this.
    """
    backend = config.vector_backend()
    if backend == "milvus":
        try:
            uri = config.milvus_uri()
            from .docker_preflight import is_local_uri
            if is_local_uri(uri):
                from .docker_preflight import ensure_milvus_ready
                ensure_milvus_ready(quiet=True)  # best-effort; a still-down server just falls through below
            return MilvusVectorStore(embeddings, texts, sections)
        except Exception as e:  # ImportError (no pymilvus) or any connection error
            sys.stderr.write(
                f"[vector_store] Milvus backend unavailable ({type(e).__name__}: {e}); "
                f"falling back to numpy.\n"
            )
            return NumpyVectorStore(embeddings, texts, sections)
    return NumpyVectorStore(embeddings, texts, sections)
