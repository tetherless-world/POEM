# POEM Embeddings — Overview

Quick reference: see `embeddings/manuals/DOCS_SUMMARY.md` for a single-page quick-start.

> Commands throughout this doc and the rest of `embeddings/` assume your repo
> checkout's **root** as the working directory (paths are written relative to
> it, e.g. `embeddings\MCP\...`) — adjust for wherever your own checkout lives.

This folder turns the POEM mental-health ontology into a **semantic search** system and exposes that search to LLMs. It is split into two halves:

```
embeddings/
├── MAIN.md            ← you are here (overview of both halves)
├── ROADMAP.md         ← next steps: building the LLM chatbot/agent on top
├── TESTING.md         ← active test runbook (how to test every layer)
├── e2e_check.py       ← one-command end-to-end acceptance harness (gates 1/2/4)
├── pyproject.toml     ← installs the shared core:  pip install -e embeddings
│
├── poem_core/         ← SHARED core package (imported by both halves)
│   ├── config.py           paths, endpoint, backend selection (one source of truth)
│   ├── embedding_client.py the one OpenAI-compatible embedding client
│   ├── metrics.py          the 4 similarity metrics + Milvus metric mapping
│   ├── corpus.py           manifest IO + load_embeddings()
│   ├── entities.py         entity-name / URI helpers
│   ├── dedup.py            get_unique_top_results()
│   ├── graph.py            load_graph() — the POEM RDF loader
│   └── vector_store.py     numpy + external-Milvus backends, get_store()
│
├── docker/
│   ├── milvus-compose.yml  ← external Milvus Standalone server (separate process)
│   ├── MILVUS.md           ← Milvus integration deep-dive (architecture, schema, verify)
│   ├── check_milvus.py     ← one-command live Milvus/Zilliz verification
│   └── milvus_admin.py     ← upload/update/switch accounts (status · push · drop)
│
├── Pipeline/          ← build & query the embeddings (thin layers over poem_core)
│   ├── PIPELINE_DOCS.md   ← full docs for this half
│   ├── generate_text_templates.py   RDF graph  → text templates
│   ├── sample_embeddings.py         endpoint connectivity check
│   ├── generate_embeddings.py       templates  → .npy embeddings
│   ├── search_similarity.py         query      → ranked results (CLI)
│   ├── evaluate_search.py           search-quality evaluation
│   ├── test_search_similarity.py    test suite
│   ├── instruments/  scales/  collections/   stored .npy embeddings + texts.npy
│   └── ... (templates, results, requirements.txt)
│
├── MCP/               ← serve the search to any LLM as an MCP tool (chatbot backend)
│   ├── MCP.md             ← full docs for this half
│   ├── LM_STUDIO.md       ← run the tools from a local LLM (LM Studio) + see params/JSON
│   ├── mcp_server.py      FastMCP server exposing `search` + `get_statements` (stdio)
│   ├── graph_lookup.py    RDF id/label resolution + get_statements
│   ├── try_search.py      quick standalone check
│   ├── requirements-mcp.txt
│   └── .venv-mcp/         dedicated Python 3.12 env (git-ignored)
│
├── API/               ← serve the search over plain HTTP/JSON (FastAPI; Swagger at /docs)
│   ├── API.md             ← full docs for this half
│   ├── api_server.py      FastAPI app: /search, /statements, /health (+ /docs UI)
│   └── requirements-api.txt
│
└── agent/             ← Phase 2 LLM agent: chat in the terminal, grounded via the tools
    ├── AGENT.md           ← full docs: running it, choosing the chat model, LM Studio roles
    ├── chat_agent.py      OpenAI-compatible chat model + tool calls → MCP server (or REST API)
    └── requirements-agent.txt
```

---

## The two halves

### `Pipeline/` — build and query the embeddings
The data pipeline and search engine. It reads the POEM RDF graph, generates natural-language templates, embeds them with the `qwen3-embedding` model on RPI's embedding server, and stores one `.npy` vector per paragraph across three sections (`instruments`, `scales`, `collections`). `search_similarity.py` then embeds a query and ranks all stored paragraphs by four similarity metrics. This half runs on **Python 3.8+** and is usable directly via CLI or by importing its functions.

→ **Full instructions:** [Pipeline/PIPELINE_DOCS.md](Pipeline/PIPELINE_DOCS.md)

### `MCP/` — serve the search to any LLM
A thin **Model Context Protocol** server that imports the shared `poem_core` package and exposes two tools — `search` and `get_statements`. Any MCP-capable client (an agent framework, a custom chatbot, etc.) — with any LLM behind it, fine-tuned or not — can discover and call them without knowing anything about numpy, Milvus, or the embedding server. This is the first step toward a POEM chatbot. Requires **Python ≥ 3.10** (hence its own `.venv-mcp`).

The **embedding LLM** behind the tool is configurable in code: bring your own OpenAI-compatible endpoint, or default to the POEM server (`idea-llm-01:11435`).

→ **Full instructions:** [MCP/MCP.md](MCP/MCP.md)

---

## How they relate

```
                         ┌───────────────────── poem_core ─────────────────────┐
RDF graph ─Pipeline─► .npy │  config · embedding client · metrics · corpus       │
                         │ │  loader · graph loader · dedup ·                    │
query ───────────────────┘ │  vector store (numpy | external Milvus, exact FLAT) │
                           └───────────────┬──────────────────┬─────────────────┘
                                  used by  │                  │  used by
                                           ▼                  ▼
                          Pipeline/search_similarity.py     MCP/mcp_server.py
                                  (CLI search)         (`search` + `get_statements` → LLM)
```

Both halves import the shared **`poem_core`** package (`embeddings/poem_core/`): config, the embedding client, similarity `METRICS`, the corpus loader (`load_embeddings`), result dedup (`get_unique_top_results`), the RDF graph loader (`load_graph`), and the pluggable vector store (`get_store`). The Pipeline/MCP entry scripts are thin layers that re-export from it, so there is exactly one implementation of each concern and no cross-folder `sys.path` hacks. Install once with `pip install -e embeddings`.

---

## Quick start

**Just want to run a search from the terminal?** Use the Pipeline (on RPI network/VPN):
```bash
python embeddings/Pipeline/search_similarity.py "instruments that measure anxiety in children"
```

**Want an LLM to call the search?** Use the MCP server:
```powershell
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\MCP\mcp_server.py
```

See each half's doc for prerequisites and details.

---

## Shared requirements

- **Embedding server:** both halves default to `http://idea-llm-01.idea.rpi.edu:11435/v1` (model `qwen3-embedding`), reachable only on the RPI network/VPN — this server is HTTP-only (verified). Endpoint/model/dir are configurable via `EMBED_BASE_URL`, `EMBED_MODEL`, `EMBEDDINGS_DIR` (set `https://…` for a TLS-capable endpoint).
- **The MCP server depends on the Pipeline's stored embeddings** — run the Pipeline through Step 3 (`generate_embeddings.py`) before relying on `search`.

## What's new

- **One canonical input folder:** `generate_text_templates.py --input` (default `poem-demo/dist/data`).
- **Generic sections:** sections beyond `instruments`/`scales`/`collections` are supported — add one with `--section NAME=poem:Class`, and search/serve auto-detect any section folder on disk.
- **Incremental embeddings:** `generate_embeddings.py --incremental` re-embeds only changed/new entities (content-hash `manifest.json` per section).
- **Richer MCP results:** `search` returns `type`/`description`/`aliases`/`snippet` alongside `id`/`label`/`section`/`score`, with an `outputSchema`.
- **Shared `poem_core` package:** config, embedding client, metrics, corpus/manifest IO, RDF graph loader, dedup, and vector store live in one place; Pipeline + MCP re-export from it (`pip install -e embeddings`).
- **External Milvus by default:** `VECTOR_BACKEND=milvus` targets a separate-process Milvus Standalone server (`embeddings/docker/milvus-compose.yml`, `http://localhost:19530`). It uses **FLAT** indexes so results are exact (identical to numpy); L1/Manhattan falls back to numpy; and if no server is reachable the store degrades to `numpy` automatically. Set `VECTOR_BACKEND=numpy` to force the in-process backend. See **[docker/MILVUS.md](docker/MILVUS.md)** for the full integration deep-dive (code map, collection schema, verification).
- **SPARQL fix:** `COLLECTION_QUERY` now follows `sio:SIO_000059` (the predicate the current data uses) for collection membership, so collection paragraphs regain their member/family/language enrichment on the next `generate_text_templates` + `generate_embeddings` run.
- **REST API (FastAPI):** `embeddings/API/` serves the same search over HTTP/JSON with an interactive Swagger UI at `/docs` — the "Milvus as an API from Python" surface (same `get_store()` backend). See [API/API.md](API/API.md).
- **Local-LLM guide:** [MCP/LM_STUDIO.md](MCP/LM_STUDIO.md) — drive the `search`/`get_statements` tools from a local model in LM Studio and watch the tool-call parameters + JSON. Embedding caveat: queries must use `qwen3-embedding` (4096-dim) to match the stored corpus.
- **Milvus deployment survey:** [docker/MILVUS.md](docker/MILVUS.md) documents the integration and the local Docker-based Standalone deployment recommended for POEM.
- **Local Milvus demo:** [docker/MILVUS_DEMO.md](docker/MILVUS_DEMO.md) explains a quick, deployable demo that shows vector search, metadata filtering, updates, and growth; [docker/milvus_demo.py](docker/milvus_demo.py) runs it locally.
- **Test runbook + Milvus checker:** [TESTING.md](TESTING.md) gives copy-paste commands to actively test every layer; [docker/check_milvus.py](docker/check_milvus.py) verifies a live local Milvus backend (selection, parity, counts, reuse) in one command.
- **LLM agent + roadmap:** [agent/chat_agent.py](agent/chat_agent.py) is a runnable terminal chatbot (local model → tool calls → grounded POEM answers) — usage, chat-model selection, and LM Studio integration in [agent/AGENT.md](agent/AGENT.md); [ROADMAP.md](ROADMAP.md) lays out the phases to grow it into the full assistant.
- **Milvus account tooling:** [docker/milvus_admin.py](docker/milvus_admin.py) — `status` / `push` / `drop` to populate a local Milvus instance, update it after data changes, or switch targets via `--uri`/`--token` or env. See [MILVUS.md §14](docker/MILVUS.md).
- **End-to-end harness:** [e2e_check.py](e2e_check.py) runs the whole-process acceptance gates (offline suites → local Milvus backend → REST surface) in one command and prints the manual steps for the CLI + agent gates. See [TESTING.md](TESTING.md) "End-to-end acceptance test".
- **MCP server is now containerizable:** [MCP/Dockerfile](MCP/Dockerfile) + [docker/mcp-compose.yml](docker/mcp-compose.yml) run the server as a network-reachable HTTP service (`MCP_TRANSPORT=http`), alongside Milvus, for deployment beyond a single local subprocess-spawning client. See [MCP/MCP.md "Container deployment"](MCP/MCP.md#container-deployment).
