#!/usr/bin/env python3
"""MCP server exposing POEM semantic search as a tool for any LLM.

Wraps the existing search functions in ``search_similarity.py`` and serves a
single ``search`` tool over the Model Context Protocol (FastMCP, stdio
transport).  An MCP-capable LLM client (an agent framework, a custom chatbot,
etc.) can then discover and call ``search`` without knowing anything about
numpy, the stored ``.npy`` embeddings, or the embedding server.

Run it:
    python embeddings/MCP/mcp_server.py      # stdio transport

Or with the dev inspector (FastMCP 3.x):
    fastmcp dev inspector embeddings/MCP/mcp_server.py

The search building blocks live in the shared ``poem_core`` package
(``embeddings/poem_core/``): the embedding client, similarity metrics, the corpus
loader, result dedup, and the vector store. This file adds the ``embeddings/``
root to sys.path so ``import poem_core...`` resolves when run as a script.

Requires Python >= 3.10 (FastMCP).

Embedding backend (the LLM that turns queries into vectors): by default the
``search`` tool uses the POEM embedding server (idea-llm-01:11435). To attach
your OWN LLM / embedding endpoint, set EMBED_LLM_BASE_URL (and EMBED_LLM_MODEL)
in the configuration block below -- any OpenAI-compatible embeddings endpoint
works. EMBEDDINGS_DIR still controls where the stored ``.npy`` vectors load from.
"""
from __future__ import annotations

import os
import sys
from typing import TypedDict
from contextlib import redirect_stdout

# The shared core package lives one level up (embeddings/poem_core). Add the
# embeddings/ root to sys.path so `import poem_core...` and the sibling
# `graph_lookup` resolve, whether this file is run as a script or imported.
_HERE = os.path.dirname(os.path.abspath(__file__))
_EMB_ROOT = os.path.dirname(_HERE)
if _EMB_ROOT not in sys.path:
    sys.path.insert(0, _EMB_ROOT)

from fastmcp import FastMCP
from starlette.requests import Request
from starlette.responses import JSONResponse

# ===========================================================================
# Embedding LLM / backend configuration   (edit here -- in code, not the terminal)
# ===========================================================================
# The `search` tool turns each query into a vector using an embedding model
# served over an OpenAI-compatible API. Choose which one to use:
#
#   * Attach your OWN LLM / embedding endpoint by setting EMBED_LLM_BASE_URL
#     (and EMBED_LLM_MODEL if the model name differs). Any OpenAI-compatible
#     embeddings endpoint works -- a local server, vLLM, Ollama, etc.
#   * Leave them as None to fall back to the default POEM embedding server.
#
# Applied below *before* importing search_similarity, which builds its OpenAI
# client from these values at import time.
EMBED_LLM_BASE_URL: str | None = None   # e.g. "http://localhost:1234/v1"
EMBED_LLM_MODEL:    str | None = None   # e.g. "nomic-embed-text"

DEFAULT_EMBED_BASE_URL = "http://idea-llm-01.idea.rpi.edu:11435/v1"
DEFAULT_EMBED_MODEL    = "qwen3-embedding"

# In-code choice wins; otherwise honor an existing env var; otherwise default.
if EMBED_LLM_BASE_URL is not None:
    os.environ["EMBED_BASE_URL"] = EMBED_LLM_BASE_URL
else:
    os.environ.setdefault("EMBED_BASE_URL", DEFAULT_EMBED_BASE_URL)

if EMBED_LLM_MODEL is not None:
    os.environ["EMBED_MODEL"] = EMBED_LLM_MODEL
else:
    os.environ.setdefault("EMBED_MODEL", DEFAULT_EMBED_MODEL)

from poem_core.metrics import METRICS
from poem_core.corpus import SECTIONS, load_embeddings
from poem_core.embedding_client import embed_query
from poem_core.dedup import get_unique_top_results
from poem_core.vector_store import get_store

# Graph access for id/label resolution and the get_statements tool. Lives in
# this folder (graph_lookup.py); wraps the same RDF graph the embedding
# templates were built from.
import graph_lookup

# ---------------------------------------------------------------------------
# Load the corpus and graph once at startup.
#
# load_embeddings() reads ~860 .npy files and graph_lookup.ensure_loaded()
# parses the POEM TTLs; both print progress to stdout.  Under the stdio
# transport, stdout is reserved for the JSON-RPC protocol, so any stray print
# would corrupt the stream -- redirect that output to stderr.
#
# Any failure here (missing .npy corpus, unreachable/empty RDF graph, vector
# store construction error) is fatal -- there is nothing useful this server
# can do without them -- so it prints a traceback plus an actionable checklist
# and exits non-zero, rather than either continuing half-loaded or letting a
# raw traceback be the only signal (important for container orchestrators,
# which need a clean, fast, unambiguous startup failure to act on).
#
# Wrapped in a function (called once, immediately below) rather than left as
# bare module-level code so tests can re-invoke it with mocked loaders to
# exercise the failure paths (see MCP/test_mcp_intensive.py) without needing a
# subprocess per scenario.
# ---------------------------------------------------------------------------
def _startup() -> None:
    global _EMB, _TEXTS, _SECS, _STORE
    try:
        with redirect_stdout(sys.stderr):
            _EMB, _TEXTS, _SECS = load_embeddings()
            print(f"[mcp_server] Loaded {len(_TEXTS)} paragraphs from {SECTIONS}")
            print(f"[mcp_server] Embedding backend: {os.environ['EMBED_MODEL']} "
                  f"@ {os.environ['EMBED_BASE_URL']}")
            graph_lookup.ensure_loaded()
            if len(graph_lookup._G) == 0:
                # The single most likely container-deployment mistake: the RDF
                # graph lives outside embeddings/ (see MCP.md "Container
                # deployment"), so a forgotten bind mount loads a graph with zero
                # triples instead of raising -- get_statements would then just
                # always report "not found", which is much harder to diagnose
                # than a startup failure.
                raise RuntimeError(
                    "graph loaded but has 0 triples -- POEM_PROJECT_ROOT="
                    f"{os.environ.get('POEM_PROJECT_ROOT', '<default>')!r} probably "
                    "isn't a real POEM checkout (e.g. a container bind mount wasn't "
                    "attached)"
                )
            print(f"[mcp_server] Loaded graph: {len(graph_lookup._G)} triples")
            # Vector backend: numpy (default) or milvus, via VECTOR_BACKEND.
            _STORE = get_store(_EMB, _TEXTS, _SECS)
            print(f"[mcp_server] Vector backend: {type(_STORE).__name__}")
    except Exception:
        import traceback
        traceback.print_exc(file=sys.stderr)
        sys.stderr.write(
            "\n[mcp_server] FATAL: startup failed loading the corpus/graph/vector "
            "store (traceback above). Common causes:\n"
            "  - EMBEDDINGS_DIR isn't pointing at Pipeline/{instruments,scales,collections}\n"
            "    (.npy files missing -- run Pipeline/generate_embeddings.py, or check\n"
            "    the container COPY/mount).\n"
            "  - POEM_PROJECT_ROOT / POEM_DATA_DIR doesn't point at a checkout\n"
            "    containing the POEM RDF TTLs (individualsFull.ttl, ontology/, POEM.rdf,\n"
            "    poem-demo/dist/data/) -- in a container this must be a bind mount.\n"
            "  - VECTOR_BACKEND=milvus but Milvus is unreachable and MILVUS_SKIP_ENSURE\n"
            "    isn't set -- try VECTOR_BACKEND=numpy, or fix MILVUS_URI.\n"
            "See MCP.md 'Prerequisites' and 'Container deployment'.\n"
        )
        sys.exit(1)


_startup()

mcp = FastMCP("POEM Embedding Search")

# Max characters of an entity description to include (keep payloads lean for
# conversational LLM use, per MCP structured-content guidance).
_DESC_MAX = 300


class SearchResult(TypedDict):
    """One search hit. Declared as a TypedDict so FastMCP emits an outputSchema
    and structured content for the `search` tool."""
    id: str
    label: str
    section: str
    score: float
    type: str | None
    description: str | None
    aliases: list[str]
    snippet: str


@mcp.tool
def search(
    query: str,
    top_k: int = 5,
    section: str | None = None,
    metric: str = "Cosine Similarity",
) -> list[SearchResult]:
    """Semantic search over POEM mental-health instruments, scales, and collections.

    Embeds the query and returns the ``top_k`` most relevant entities, ranked by
    similarity and deduplicated by entity (so one instrument does not fill every
    slot). Use this to find questionnaires/scales relevant to a topic, symptom,
    or item wording, then pass a result's ``id`` to ``get_statements`` to read
    that entity's relationships from the graph.

    Args:
        query: Natural-language search text (a topic, symptom, or item wording).
        top_k: Number of unique entities to return (default 5).
        section: Restrict to one of the available sections (commonly
            'instruments', 'scales', 'collections'; others may exist). Omit to
            search all sections.
        metric: Similarity metric. One of 'Cosine Similarity' (default),
            'Dot Product', 'Euclidean (L2)', 'Manhattan (L1)'.

    Returns:
        A list of result dicts ordered best-first, each with:
          - id:          the entity's skos:notation (e.g. 'RCADS-25-CG-EN', 'SP');
                         pass this to get_statements.
          - label:       human-readable rdfs:label (e.g. 'Social Phobia (9.1)').
          - section:     the section the hit came from (e.g. 'instruments').
          - score:       similarity score (float; higher is more similar).
          - type:        the entity's readable rdf:type, or null.
          - description: a short description from the ontology, or null.
          - aliases:     up to 3 alternative labels (may be empty).
          - snippet:     a preview of the matched paragraph text.
    """
    if metric not in METRICS:
        raise ValueError(
            f"Unknown metric {metric!r}. Choose one of: {list(METRICS)}"
        )
    if section is not None and section not in SECTIONS:
        raise ValueError(
            f"Unknown section {section!r}. Choose one of: {SECTIONS}"
        )
    if top_k < 1:
        raise ValueError(f"top_k must be >= 1, got {top_k}")

    query_vec = embed_query(query)

    # Pull a wider candidate window from the vector store so dedup-by-entity
    # still yields top_k uniques. The numpy backend scores the whole corpus;
    # the milvus backend returns its top-`window` hits.
    window = max(20, top_k * 4)
    scores, txt, sec = _STORE.top_candidates(query_vec, metric, section, k=window)

    raw = get_unique_top_results(
        scores,
        txt,
        sec,
        top_k_search=window,
        top_k_unique=top_k,
    )

    # Resolve each hit's entity string to a stable (id, label) + enrichment.
    results: list[SearchResult] = []
    for r in raw:
        info = graph_lookup.resolve_entity_rich(r["entity"], r["section"])
        desc = info["description"]
        if desc and len(desc) > _DESC_MAX:
            desc = desc[:_DESC_MAX - 1].rstrip() + "…"
        results.append({
            "id": info["id"],
            "label": info["label"],
            "section": r["section"],
            "score": r["score"],
            "type": info["type"],
            "description": desc,
            "aliases": info["aliases"],
            "snippet": r["preview"],
        })
    return results


@mcp.tool
def get_statements(entity_id: str, lang: str = "en") -> list[dict]:
    """Return a POEM entity's immediate relationships from the knowledge graph.

    Call this after ``search`` to describe one of its results: pass the result's
    ``id`` (a skos:notation such as 'RCADS-25-CG-EN' or 'SP'). Performs a direct
    lookup in the POEM RDF graph and returns the entity's immediate outgoing
    statements (property-value pairs).

    Args:
        entity_id: An id from a prior ``search`` result (skos:notation; also
            accepts an fhir:code or rdfs:label).
        lang: Language for labels (accepted for forward-compatibility; labels
            are currently returned as stored in the graph).

    Returns:
        A list of ``{property, value, value_id}`` dicts. ``value_id`` is the
        object's own id when the object is itself a graph entity (so it can be
        fed back into ``get_statements`` to traverse the graph), and null for
        plain literal values. Raises ValueError if the id matches no entity.
    """
    return graph_lookup.get_statements(entity_id)


# ---------------------------------------------------------------------------
# HTTP-only liveness probe (see "Container deployment" in MCP.md). Inert under
# stdio transport -- no HTTP server is running in that mode, so nothing calls
# this route; it costs nothing to register unconditionally.
# ---------------------------------------------------------------------------
@mcp.custom_route("/health", methods=["GET"])
async def health(_request: Request) -> JSONResponse:
    """Liveness probe for HTTP-transport deployments (container HEALTHCHECK etc.).

    A 200 only confirms the process is alive and finished loading at startup --
    a load failure exits non-zero before the server ever starts listening (see
    the try/except around the corpus/graph/store loading above). Does NOT
    probe the embedding endpoint the `search` tool depends on; that's a
    separate, optional concern (see MCP.md).
    """
    return JSONResponse({
        "status": "ok",
        "paragraphs": len(_TEXTS),
        "graph_triples": len(graph_lookup._G),
        "vector_backend": type(_STORE).__name__,
    })


# ---------------------------------------------------------------------------
# Transport selection (see "Container deployment" in MCP.md).
#
#   MCP_TRANSPORT=stdio (default) -- unchanged behavior: the client (LM Studio,
#     agent/chat_agent.py, try_search.py) launches this file as a subprocess
#     and talks over stdin/stdout. Nothing below runs differently than before.
#   MCP_TRANSPORT=http            -- serves over HTTP at http://HOST:PORT/mcp
#     for a containerized / remotely-reachable deployment. No auth/TLS here --
#     that's Phase 5 hardening territory (see ../ROADMAP.md), not this file.
#
# MCP_PORT defaults to 8100, not 8000 -- API/api_server.py already uses 8000,
# and both surfaces may run on the same host at once (see ../ROADMAP.md's
# three-serving-surfaces design).
#
# Split into a pure function (env -> validated settings, or ValueError) so
# tests can exercise the "unknown MCP_TRANSPORT" validation directly, without
# needing to run this file as a subprocess (see MCP/test_mcp_intensive.py).
# ---------------------------------------------------------------------------
def _resolve_transport() -> tuple[str, str, int]:
    transport = os.environ.get("MCP_TRANSPORT", "stdio").lower()
    host = os.environ.get("MCP_HOST", "127.0.0.1")
    port = int(os.environ.get("MCP_PORT", "8100"))
    if transport not in ("stdio", "http"):
        raise ValueError(
            f"Unknown MCP_TRANSPORT={transport!r}; expected 'stdio' or 'http'."
        )
    return transport, host, port


if __name__ == "__main__":
    try:
        _transport, _host, _port = _resolve_transport()
    except ValueError as e:
        sys.exit(f"[mcp_server] {e}")

    if _transport == "stdio":
        mcp.run()  # stdio transport (default, unchanged)
    else:
        print(f"[mcp_server] Serving over HTTP at http://{_host}:{_port}/mcp "
              f"(health: http://{_host}:{_port}/health)", file=sys.stderr)
        mcp.run(transport="http", host=_host, port=_port)
