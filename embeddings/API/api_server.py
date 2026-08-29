#!/usr/bin/env python3
"""FastAPI REST API exposing POEM semantic search over plain HTTP/JSON.

A third serving layer alongside the CLI (``Pipeline/search_similarity.py``) and
the MCP server (``MCP/mcp_server.py``). It reuses the exact same building blocks —
the shared ``poem_core`` package (embedding client, similarity metrics, corpus
loader, dedup, and the pluggable vector store) plus the RDF enrichment in
``MCP/graph_lookup.py`` — and exposes them as ordinary HTTP endpoints so any
client (curl, a browser, another service) can query without speaking MCP.

This is the "Milvus as an API from Python" surface: the same `get_store()` that
talks to Milvus (or numpy) backs ``/search`` here.

Run it (from the MCP venv, which has fastapi/uvicorn + the poem_core deps):

    o:\\POEM\\embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe o:\\POEM\\embeddings\\API\\api_server.py

or with autoreload for development:

    o:\\POEM\\embeddings\\MCP\\.venv-mcp\\Scripts\\python.exe -m uvicorn api_server:app \\
        --app-dir o:\\POEM\\embeddings\\API --reload

Then open the interactive web UI (Swagger) at http://localhost:8000/docs
(ReDoc at /redoc). Endpoints:

    GET  /health            backend / corpus / embedding-endpoint status
    GET  /sections          available section names
    GET  /metrics           available similarity-metric names
    POST /search            body {query, top_k, section, metric} -> ranked hits
    GET  /search            same via query params (browser-friendly)
    GET  /statements/{id}   an entity's immediate graph relationships

Configuration is identical to the rest of the stack (env vars, read live):
    EMBED_BASE_URL / EMBED_MODEL   the OpenAI-compatible embedding endpoint
    VECTOR_BACKEND                 'milvus' (default) or 'numpy'
    MILVUS_URI / MILVUS_TOKEN      the Milvus server, when VECTOR_BACKEND=milvus
    API_PORT                       HTTP port (default 8000)

Requires Python >= 3.10.
"""
from __future__ import annotations

import os
import sys
from typing import Optional
from contextlib import redirect_stdout

# poem_core lives at embeddings/ (one level up from API/); graph_lookup lives in
# the sibling MCP/ folder. Add both so imports resolve when run from anywhere.
# (graph_lookup is POEM-specific RDF enrichment shared with the MCP server; it is
# imported from MCP/ rather than duplicated here.)
_HERE = os.path.dirname(os.path.abspath(__file__))
_EMB_ROOT = os.path.dirname(_HERE)
_MCP_DIR = os.path.join(_EMB_ROOT, "MCP")
for _p in (_EMB_ROOT, _MCP_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

from poem_core.metrics import METRICS
from poem_core.corpus import SECTIONS, load_embeddings
from poem_core.embedding_client import embed_query
from poem_core.dedup import get_unique_top_results
from poem_core.vector_store import get_store
from poem_core import config
import graph_lookup

# Keep descriptions lean in API payloads (same cap the MCP tool uses).
_DESC_MAX = 300

# --- Load corpus, graph, and vector store once at startup -------------------
# load_embeddings() and the graph loader print progress; redirect to stderr so
# server logs stay clean and structured.
with redirect_stdout(sys.stderr):
    _EMB, _TEXTS, _SECS = load_embeddings()
    graph_lookup.ensure_loaded()
    _STORE = get_store(_EMB, _TEXTS, _SECS)
    _BACKEND = type(_STORE).__name__
    print(f"[api_server] {len(_TEXTS)} paragraphs from {SECTIONS}; "
          f"backend={_BACKEND}; embed={config.EMBED_MODEL} "
          f"@ {config.EMBED_BASE_URL}", file=sys.stderr)


# --- Schemas ----------------------------------------------------------------
class SearchResult(BaseModel):
    id: str
    label: str
    section: str
    score: float
    type: Optional[str] = None
    description: Optional[str] = None
    aliases: list[str] = []
    snippet: str


class SearchRequest(BaseModel):
    query: str = Field(..., description="Natural-language search text (topic, symptom, or item wording).")
    top_k: int = Field(5, ge=1, description="Number of unique entities to return.")
    section: Optional[str] = Field(None, description=f"Restrict to one of {SECTIONS}; omit to search all.")
    metric: str = Field("Cosine Similarity", description=f"Similarity metric; one of {list(METRICS)}.")


class Statement(BaseModel):
    property: str
    value: str
    value_id: Optional[str] = None


app = FastAPI(
    title="POEM Semantic Search API",
    version="1.0.0",
    description=(
        "HTTP/JSON access to POEM instrument/scale/collection semantic search — the "
        "same engine (poem_core + Milvus/numpy vector store) behind the CLI and the "
        "MCP server. Use the **Try it out** buttons below to query live."
    ),
)


def _run_search(query: str, top_k: int, section: Optional[str], metric: str) -> list[SearchResult]:
    """Shared orchestration for both /search verbs — mirrors MCP `search`."""
    if metric not in METRICS:
        raise HTTPException(422, f"Unknown metric {metric!r}. Choose one of: {list(METRICS)}")
    if section is not None and section not in SECTIONS:
        raise HTTPException(422, f"Unknown section {section!r}. Choose one of: {SECTIONS}")
    if top_k < 1:
        raise HTTPException(422, "top_k must be >= 1")

    try:
        query_vec = embed_query(query)
    except Exception as e:  # embedding endpoint down / off-network
        raise HTTPException(
            503,
            f"Embedding endpoint unavailable ({type(e).__name__}: {e}). "
            f"Point EMBED_BASE_URL/EMBED_MODEL at a reachable OpenAI-compatible server.",
        )

    # Widen the candidate window so dedup-by-entity still yields top_k uniques.
    window = max(20, top_k * 4)
    scores, txt, sec = _STORE.top_candidates(query_vec, metric, section, k=window)
    raw = get_unique_top_results(scores, txt, sec, top_k_search=window, top_k_unique=top_k)

    results: list[SearchResult] = []
    for r in raw:
        info = graph_lookup.resolve_entity_rich(r["entity"], r["section"])
        desc = info["description"]
        if desc and len(desc) > _DESC_MAX:
            desc = desc[:_DESC_MAX - 1].rstrip() + "…"
        results.append(SearchResult(
            id=info["id"], label=info["label"], section=r["section"], score=float(r["score"]),
            type=info["type"], description=desc, aliases=info["aliases"], snippet=r["preview"],
        ))
    return results


@app.get("/health", summary="Service + backend status")
def health() -> dict:
    return {
        "status": "ok",
        "vector_backend": _BACKEND,
        "num_vectors": int(len(_TEXTS)),
        "sections": SECTIONS,
        "metrics": list(METRICS),
        "embed_model": config.EMBED_MODEL,
        "embed_base_url": config.EMBED_BASE_URL,
    }


@app.get("/sections", summary="Available section names")
def sections() -> dict:
    return {"sections": SECTIONS}


@app.get("/metrics", summary="Available similarity metrics")
def metric_names() -> dict:
    return {"metrics": list(METRICS)}


@app.post("/search", response_model=list[SearchResult], summary="Semantic search (JSON body)")
def search_post(req: SearchRequest) -> list[SearchResult]:
    return _run_search(req.query, req.top_k, req.section, req.metric)


@app.get("/search", response_model=list[SearchResult], summary="Semantic search (query params)")
def search_get(
    query: str = Query(..., description="Natural-language search text."),
    top_k: int = Query(5, ge=1, description="Number of unique entities to return."),
    section: Optional[str] = Query(None, description=f"Restrict to one of {SECTIONS}."),
    metric: str = Query("Cosine Similarity", description=f"One of {list(METRICS)}."),
) -> list[SearchResult]:
    return _run_search(query, top_k, section, metric)


@app.get("/statements/{entity_id}", response_model=list[Statement], summary="An entity's graph relationships")
def statements(entity_id: str) -> list[Statement]:
    try:
        return graph_lookup.get_statements(entity_id)
    except ValueError as e:
        raise HTTPException(404, str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=int(os.environ.get("API_PORT", "8000")))
