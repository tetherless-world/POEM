# POEM Semantic Search — REST API

Quick reference: see `../manuals/DOCS_SUMMARY.md` for a concise API quick-start and health checks.

A **FastAPI** HTTP/JSON layer over the POEM search engine. It's a third serving
surface alongside the CLI ([`Pipeline/search_similarity.py`](../Pipeline/search_similarity.py))
and the MCP server ([`MCP/mcp_server.py`](../MCP/mcp_server.py)), reusing the **exact
same** engine — `poem_core` (embedding client, metrics, corpus loader, dedup, and
the Milvus/numpy vector store) plus the RDF enrichment in
[`MCP/graph_lookup.py`](../MCP/graph_lookup.py).

This is the **"Milvus as an API from Python"** surface: the same `get_store()` that
talks to Milvus (or numpy) backs `/search` here, exposed as ordinary HTTP so any
client — curl, a browser, another service — can query without speaking MCP.

> **Web UI for free:** FastAPI serves an interactive **Swagger UI at `/docs`**
> (and ReDoc at `/redoc`). That *is* the "web interface to try the query
> functions" — open it, hit **Try it out**, fill parameters, and see the JSON.

---

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Backend, corpus size, sections, metrics, embedding endpoint |
| `GET` | `/sections` | Available section names |
| `GET` | `/metrics` | Available similarity-metric names |
| `POST` | `/search` | Body `{query, top_k, section, metric}` → ranked hits |
| `GET` | `/search` | Same via query params (browser-friendly) |
| `GET` | `/statements/{entity_id}` | An entity's immediate graph relationships |
| `GET` | `/docs` · `/redoc` | Interactive API UI (Swagger / ReDoc) |

A `/search` hit is the same shape the MCP `search` tool returns:
`id, label, section, score, type, description, aliases, snippet`.
`/statements` returns `{property, value, value_id}` triples.

## Run it

Use the MCP venv (Python 3.12) — it already has FastAPI + uvicorn + the search stack:

```powershell
# Direct (from the repo root)
embeddings\MCP\.venv-mcp\Scripts\python.exe embeddings\API\api_server.py

# Or with autoreload (dev)
embeddings\MCP\.venv-mcp\Scripts\python.exe -m uvicorn api_server:app `
  --app-dir embeddings\API --reload
```

Then open **http://localhost:8000/docs**. (Change the port with `API_PORT`.)

Fresh env instead? `pip install -r embeddings/API/requirements-api.txt`
(add `--trusted-host pypi.org --trusted-host files.pythonhosted.org` behind an
SSL-intercepting network).

## Configuration (env vars, read live)

| Var | Default | Purpose |
|---|---|---|
| `EMBED_BASE_URL` | `http://idea-llm-01.idea.rpi.edu:11435/v1` | OpenAI-compatible embedding endpoint (used by `/search`) |
| `EMBED_MODEL` | `qwen3-embedding` | Embedding model — **must match the model that built the stored vectors** (see caveat) |
| `VECTOR_BACKEND` | `milvus` | `milvus` (external server) or `numpy` (in-process) |
| `MILVUS_URI` / `MILVUS_TOKEN` | `http://localhost:19530` / — | Milvus server, when `VECTOR_BACKEND=milvus` |
| `API_PORT` | `8000` | HTTP port |

> **Embedding-model compatibility.** The stored corpus is **4096-dim
> `qwen3-embedding`**. `/search` embeds the *query* with `EMBED_MODEL` and compares
> it to those vectors, so `EMBED_MODEL` must be the **same model** (same dimension).
> Pointing it at a different embedder (e.g. `nomic-embed-text`, 768-dim) makes
> `/search` fail or return nonsense. See [../docker/MILVUS.md](../docker/MILVUS.md)
> and [../MCP/LM_STUDIO.md](../MCP/LM_STUDIO.md).

## Examples

```bash
# Health / discovery (work offline; no embedding endpoint needed)
curl http://localhost:8000/health
curl http://localhost:8000/sections

# Search (needs a reachable embedding endpoint)
curl "http://localhost:8000/search?query=anxiety%20in%20children&top_k=3&section=instruments"

curl -X POST http://localhost:8000/search \
  -H "Content-Type: application/json" \
  -d '{"query":"caregiver report of depression","top_k":3,"section":"instruments"}'

# Describe a hit from the graph (offline)
curl http://localhost:8000/statements/RCADS-25-CG-EN
```

## Behavior notes

- **Startup** loads the `.npy` corpus, the RDF graph, and builds the vector store
  **once** (like the MCP server). First request is fast thereafter.
- `/health`, `/sections`, `/metrics`, `/statements` work **offline**. `/search`
  needs the embedding endpoint; if it's unreachable the response is a clean
  **503** telling you to fix `EMBED_BASE_URL`/`EMBED_MODEL`.
- Bad `metric`/`section`/`top_k` → **422** with the valid choices; unknown id on
  `/statements` → **404**.
- With `VECTOR_BACKEND=milvus` and no server reachable, the store logs a warning
  and falls back to numpy (see [../docker/MILVUS.md](../docker/MILVUS.md)).

> **Verified** (2026-07-06, `.venv-mcp` Python 3.12, `VECTOR_BACKEND=numpy`, via
> FastAPI `TestClient`): `/health`, `/sections`, `/metrics` → 200; `/statements`
> resolves 40 relationships for `RCADS-25-CG-EN` and 404s an unknown id; `/search`
> (embed monkeypatched to a stored vector) returns ranked hits with `GAD-7` #1 at
> score 1.0; invalid metric → 422. Live `/search` against the real embedding
> endpoint needs the RPI network.
